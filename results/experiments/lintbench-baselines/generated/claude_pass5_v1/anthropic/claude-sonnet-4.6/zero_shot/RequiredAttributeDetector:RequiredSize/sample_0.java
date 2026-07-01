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
import static com.android.SdkConstants.FD_RES_LAYOUT;
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
 * Checks whether layout views have required attributes such as layout_width and layout_height.
 */
public class RequiredAttributeDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, "
                    + "an exception is thrown at runtime.\n"
                    + "\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a "
                    + "special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_RESOURCE_FILES)));

    /** Map from style name to set of attributes defined in that style */
    private Map<String, Set<String>> mStyleToAttributes;

    /** Map from style name to parent style name */
    private Map<String, String> mStyleParents;

    /**
     * Set of layout files that have already been checked in the first pass
     * (style file pass); layout files that contain elements which specify a
     * style attribute that may contain layout_width/layout_height will be
     * recorded here so we can do a second pass.
     */
    private Set<File> mPendingLayouts;

    /**
     * List of pending errors: nodes that may or may not have layout_width/
     * layout_height depending on the style resolution. These will be reported
     * if the style does not provide the attributes.
     */
    private List<PendingError> mPendingErrors;

    /** Whether we have seen any style files */
    private boolean mHaveSeenStyleFiles;

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    /**
     * A pending error: a layout element that is missing layout_width or
     * layout_height, but which specifies a style - and that style may or may
     * not define the missing attribute.
     */
    private static class PendingError {
        public final XmlContext context;
        public final Element element;
        public final String style;
        public final boolean missingWidth;
        public final boolean missingHeight;

        PendingError(
                XmlContext context,
                Element element,
                String style,
                boolean missingWidth,
                boolean missingHeight) {
            this.context = context;
            this.element = element;
            this.style = style;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Visit style files to build up style -> attributes map
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            mHaveSeenStyleFiles = true;
            // The element visitor will handle individual style items
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // Processing style files
            handleStyleElement(element);
            return;
        }

        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        // Skip the root element if it's a merge tag
        String tag = element.getTagName();
        if (VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout does not require layout_width and layout_height
        if (GRID_LAYOUT.equals(tag) || tag.endsWith(".GridLayout")) {
            return;
        }

        // Check if this is the root element
        Node parentNode = element.getParentNode();
        if (parentNode != null && parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            // Root element - doesn't need layout_width/layout_height
            // Actually, root elements DO need these attributes (unless it's a merge)
            // Let's check: root elements in layouts also need layout_width/layout_height
            // because they will be inflated into a parent at runtime.
            // However, some special root tags don't need them.
            // For simplicity, we check all non-merge elements.
        }

        // Check if the element is a direct child of merge - those need layout params
        // Check if element has layout_width and layout_height
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if there's a style attribute that might provide the missing attributes
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            // We might need to defer this check until after all style files are processed
            if (mPendingErrors == null) {
                mPendingErrors = new ArrayList<>();
            }
            if (mPendingLayouts == null) {
                mPendingLayouts = new HashSet<>();
            }
            mPendingLayouts.add(context.file);
            mPendingErrors.add(new PendingError(
                    context, element, style, !hasWidth, !hasHeight));
            return;
        }

        // No style, and missing width or height - report immediately
        report(context, element, !hasWidth, !hasHeight);
    }

    private void handleStyleElement(@NonNull Element element) {
        // We're looking for <style> elements
        if (!TAG_STYLE.equals(element.getTagName())) {
            // Could be an item inside a style
            return;
        }

        String styleName = element.getAttribute("name");
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        // Normalize the style name (replace dots with underscores for lookup)
        // Actually keep as-is, just use the name directly

        String parent = element.getAttribute("parent");

        if (mStyleToAttributes == null) {
            mStyleToAttributes = new HashMap<>();
        }
        if (mStyleParents == null) {
            mStyleParents = new HashMap<>();
        }

        Set<String> attributes = mStyleToAttributes.get(styleName);
        if (attributes == null) {
            attributes = new HashSet<>();
            mStyleToAttributes.put(styleName, attributes);
        }

        if (parent != null && !parent.isEmpty()) {
            mStyleParents.put(styleName, parent);
        } else {
            // Check if the style name itself implies a parent via dot notation
            // e.g., "MyStyle.Child" implies parent "MyStyle"
            int dotIndex = styleName.lastIndexOf('.');
            if (dotIndex != -1) {
                String impliedParent = styleName.substring(0, dotIndex);
                mStyleParents.put(styleName, impliedParent);
            }
        }

        // Now look for item children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                if ("item".equals(item.getTagName())) {
                    String name = item.getAttribute("name");
                    if (name != null) {
                        // Normalize: remove android: prefix for comparison
                        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                            name = name.substring(ANDROID_NS_NAME_PREFIX.length());
                        }
                        attributes.add(name);
                    }
                }
            }
        }
    }

    /**
     * Check if a style (by name) provides the given attribute, checking parent
     * styles as well.
     */
    private boolean styleDefinesAttribute(@NonNull String styleName, @NonNull String attribute) {
        if (mStyleToAttributes == null) {
            return false;
        }

        // Normalize style reference: strip @style/ or @android:style/ prefix
        String name = normalizeStyleName(styleName);

        // Prevent infinite loops with a visited set
        Set<String> visited = new HashSet<>();
        return styleDefinesAttributeInternal(name, attribute, visited);
    }

    private boolean styleDefinesAttributeInternal(
            @NonNull String styleName,
            @NonNull String attribute,
            @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        if (mStyleToAttributes == null) {
            return false;
        }

        Set<String> attributes = mStyleToAttributes.get(styleName);
        if (attributes != null && attributes.contains(attribute)) {
            return true;
        }

        // Check parent style
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                String normalizedParent = normalizeStyleName(parent);
                if (!normalizedParent.isEmpty()) {
                    if (styleDefinesAttributeInternal(normalizedParent, attribute, visited)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @NonNull
    private static String normalizeStyleName(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            // Android framework styles - we can't check these, assume they might have it
            return "";
        }
        return style;
    }

    private void report(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean missingWidth,
            boolean missingHeight) {
        assert missingWidth || missingHeight;

        String message;
        if (missingWidth && missingHeight) {
            message = "The required `layout_width` and `layout_height` attributes are missing";
        } else if (missingWidth) {
            message = "The required `layout_width` attribute is missing";
        } else {
            message = "The required `layout_height` attribute is missing";
        }

        Location location = context.getLocation(element);
        context.report(ISSUE, element, location, message);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Process any pending errors now that we've seen all style files
        if (mPendingErrors != null) {
            for (PendingError pending : mPendingErrors) {
                boolean missingWidth = pending.missingWidth;
                boolean missingHeight = pending.missingHeight;

                if (missingWidth) {
                    // Check if the style provides layout_width
                    if (styleDefinesAttribute(pending.style, ATTR_LAYOUT_WIDTH)) {
                        missingWidth = false;
                    }
                }

                if (missingHeight) {
                    // Check if the style provides layout_height
                    if (styleDefinesAttribute(pending.style, ATTR_LAYOUT_HEIGHT)) {
                        missingHeight = false;
                    }
                }

                if (missingWidth || missingHeight) {
                    report(pending.context, pending.element, missingWidth, missingHeight);
                }
            }
        }
    }
}