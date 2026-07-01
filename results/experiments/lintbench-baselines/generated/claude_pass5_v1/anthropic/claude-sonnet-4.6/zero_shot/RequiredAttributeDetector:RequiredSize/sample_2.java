/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detector which finds missing layout_width and layout_height attributes in
 * layout XML files.
 */
public class RequiredAttributeDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, an "
                    + "exception is thrown at runtime.\n"
                    + "\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a "
                    + "special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_RESOURCE_FILES)));

    /**
     * Map from style name (in the current file) to the set of attribute names
     * defined in that style.
     */
    @Nullable
    private Map<String, Set<String>> mStyleToAttrMap;

    /**
     * Map from style name (in the current file) to the parent style name, if any.
     */
    @Nullable
    private Map<String, String> mStyleParentMap;

    /**
     * Set of style names (globally) that define layout_width and/or layout_height.
     */
    @Nullable
    private Set<String> mStylesWithWidth;

    /**
     * Set of style names (globally) that define layout_height.
     */
    @Nullable
    private Set<String> mStylesWithHeight;

    /**
     * Pending list of elements to check after we've processed all style files.
     * Each entry is a pair: the XmlContext and the Element.
     */
    @Nullable
    private List<PendingCheck> mPendingChecks;

    /** Whether we are currently visiting a style resource file */
    private boolean mInStyleFile;

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    // ---- Implements XmlDetector ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType type = context.getResourceFolderType();
        mInStyleFile = type == ResourceFolderType.VALUES;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mInStyleFile) {
            // Process style definitions
            String tag = element.getTagName();
            if (TAG_STYLE.equals(tag)) {
                processStyleDefinition(element);
            }
            return;
        }

        // We're in a layout file - check for missing layout_width / layout_height
        String tag = element.getTagName();

        // The root element of a layout file doesn't always need layout_width/height
        // if it's a merge tag
        if (VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout doesn't require explicit width/height
        if (isGridLayout(tag)) {
            return;
        }

        // Check parent - if parent is a GridLayout, child doesn't need width/height
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String parentTag = parent.getTagName();
            if (isGridLayout(parentTag)) {
                return;
            }
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if defined via style attribute
        String styleValue = element.getAttribute(ATTR_STYLE);
        if (styleValue != null && !styleValue.isEmpty()) {
            // We might need to defer this check until after all styles are processed
            if (!hasWidth || !hasHeight) {
                if (mPendingChecks == null) {
                    mPendingChecks = new ArrayList<>();
                }
                mPendingChecks.add(new PendingCheck(context, element, styleValue,
                        hasWidth, hasHeight));
                return;
            }
        }

        // No style, just report missing attributes
        if (!hasWidth) {
            reportMissing(context, element, ATTR_LAYOUT_WIDTH);
        }
        if (!hasHeight) {
            reportMissing(context, element, ATTR_LAYOUT_HEIGHT);
        }
    }

    /**
     * Processes a &lt;style&gt; element to record which attributes it defines.
     */
    private void processStyleDefinition(@NonNull Element styleElement) {
        String styleName = styleElement.getAttribute("name");
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Normalize the style name (replace '.' with nothing - keep as is for lookup)
        if (mStyleToAttrMap == null) {
            mStyleToAttrMap = new HashMap<>();
        }
        if (mStyleParentMap == null) {
            mStyleParentMap = new HashMap<>();
        }

        Set<String> attrs = new HashSet<>();

        // Check parent attribute
        String parent = styleElement.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            // Normalize parent reference
            parent = normalizeStyleName(parent);
            mStyleParentMap.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation
            int dotIndex = styleName.lastIndexOf('.');
            if (dotIndex >= 0) {
                String implicitParent = styleName.substring(0, dotIndex);
                mStyleParentMap.put(styleName, implicitParent);
            }
        }

        // Iterate over child <item> elements
        NodeList children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute("name");
                if (itemName != null) {
                    // Strip android: prefix for comparison
                    if (itemName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        itemName = itemName.substring(ANDROID_NS_NAME_PREFIX.length());
                    }
                    attrs.add(itemName);
                }
            }
        }

        mStyleToAttrMap.put(styleName, attrs);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now process the pending checks with full style information
        if (mPendingChecks != null) {
            for (PendingCheck check : mPendingChecks) {
                processPendingCheck(check);
            }
            mPendingChecks = null;
        }
    }

    private void processPendingCheck(@NonNull PendingCheck check) {
        boolean hasWidth = check.hasWidth;
        boolean hasHeight = check.hasHeight;

        if (!hasWidth || !hasHeight) {
            String styleRef = check.styleValue;
            String styleName = normalizeStyleName(styleRef);

            if (!hasWidth) {
                hasWidth = styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH, new HashSet<>());
            }
            if (!hasHeight) {
                hasHeight = styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT, new HashSet<>());
            }
        }

        if (!hasWidth) {
            reportMissing(check.context, check.element, ATTR_LAYOUT_WIDTH);
        }
        if (!hasHeight) {
            reportMissing(check.context, check.element, ATTR_LAYOUT_HEIGHT);
        }
    }

    /**
     * Returns whether the given style (or any of its ancestors) defines the
     * given attribute.
     */
    private boolean styleDefinesAttribute(
            @NonNull String styleName,
            @NonNull String attribute,
            @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        // Check local style map first
        if (mStyleToAttrMap != null) {
            Set<String> attrs = mStyleToAttrMap.get(styleName);
            if (attrs != null && attrs.contains(attribute)) {
                return true;
            }
        }

        // Check global sets built during afterCheckProject
        if (ATTR_LAYOUT_WIDTH.equals(attribute) && mStylesWithWidth != null
                && mStylesWithWidth.contains(styleName)) {
            return true;
        }
        if (ATTR_LAYOUT_HEIGHT.equals(attribute) && mStylesWithHeight != null
                && mStylesWithHeight.contains(styleName)) {
            return true;
        }

        // Check parent style
        if (mStyleParentMap != null) {
            String parent = mStyleParentMap.get(styleName);
            if (parent != null) {
                return styleDefinesAttribute(parent, attribute, visited);
            }
        }

        return false;
    }

    /**
     * Normalizes a style reference string to a simple style name for lookup.
     */
    @NonNull
    private static String normalizeStyleName(@NonNull String styleRef) {
        if (styleRef.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleRef.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleRef.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return styleRef.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        // Replace '/' with '.' for style references like "style/MyStyle"
        return styleRef.replace('/', '.');
    }

    /**
     * Returns whether the given tag name represents a GridLayout.
     */
    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    /**
     * Reports a missing layout attribute.
     */
    private static void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String attribute) {
        String message = String.format(
                "This `%1$s` does not have a `%2$s` attribute",
                element.getTagName(), attribute);
        Location location = context.getElementLocation(element);
        context.report(ISSUE, element, location, message);
    }

    // ---- Data class for deferred checks ----

    private static class PendingCheck {
        @NonNull final XmlContext context;
        @NonNull final Element element;
        @NonNull final String styleValue;
        final boolean hasWidth;
        final boolean hasHeight;

        PendingCheck(
                @NonNull XmlContext context,
                @NonNull Element element,
                @NonNull String styleValue,
                boolean hasWidth,
                boolean hasHeight) {
            this.context = context;
            this.element = element;
            this.styleValue = styleValue;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }
}