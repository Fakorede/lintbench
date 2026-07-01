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
import static com.android.SdkConstants.REQUEST_FOCUS;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks whether layout attributes required by views are present.
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
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from style name to set of attributes defined in that style.
     * This is used to check whether layout_width and layout_height are
     * provided via a style reference.
     */
    @Nullable
    private Map<String, Set<String>> mStyleToAttrMap;

    /**
     * Map from style name to parent style name (for style inheritance).
     */
    @Nullable
    private Map<String, String> mStyleParentMap;

    /** Styles that have been checked and confirmed to include layout_width */
    @Nullable
    private Set<String> mWidthStyles;

    /** Styles that have been checked and confirmed to include layout_height */
    @Nullable
    private Set<String> mHeightStyles;

    /** Styles that have been checked and do NOT include layout_width */
    @Nullable
    private Set<String> mNoWidthStyles;

    /** Styles that have been checked and do NOT include layout_height */
    @Nullable
    private Set<String> mNoHeightStyles;

    /**
     * List of pending elements to check after all style files have been processed.
     * Each entry is a pair of (XmlContext, Element).
     */
    @Nullable
    private List<PendingCheck> mPendingChecks;

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    private static class PendingCheck {
        final XmlContext context;
        final Element element;
        final String styleName;

        PendingCheck(XmlContext context, Element element, String styleName) {
            this.context = context;
            this.element = element;
            this.styleName = styleName;
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
        // Handle values files (styles)
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            // Parse style definitions
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String tagName = element.getTagName();
                    if ("style".equals(tagName)) {
                        processStyleElement(element);
                    }
                }
            }
        }
    }

    private void processStyleElement(@NonNull Element styleElement) {
        String styleName = styleElement.getAttribute("name");
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        if (mStyleToAttrMap == null) {
            mStyleToAttrMap = new HashMap<>();
        }
        if (mStyleParentMap == null) {
            mStyleParentMap = new HashMap<>();
        }

        Set<String> attrs = new HashSet<>();
        mStyleToAttrMap.put(styleName, attrs);

        // Check for parent attribute
        String parent = styleElement.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            // Strip the prefix if present
            if (parent.startsWith(STYLE_RESOURCE_PREFIX)) {
                parent = parent.substring(STYLE_RESOURCE_PREFIX.length());
            } else if (parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
                parent = parent.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
            }
            mStyleParentMap.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation: "ParentStyle.ChildStyle"
            int dotIndex = styleName.lastIndexOf('.');
            if (dotIndex > 0) {
                String implicitParent = styleName.substring(0, dotIndex);
                mStyleParentMap.put(styleName, implicitParent);
            }
        }

        // Collect item names
        NodeList items = styleElement.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element itemElement = (Element) item;
                if ("item".equals(itemElement.getTagName())) {
                    String itemName = itemElement.getAttribute("name");
                    if (itemName != null && !itemName.isEmpty()) {
                        // Normalize: strip android: prefix for comparison
                        if (itemName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                            itemName = itemName.substring(ANDROID_NS_NAME_PREFIX.length());
                        }
                        attrs.add(itemName);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // Skip tags that don't need layout_width/layout_height
        if (REQUEST_FOCUS.equals(tag)) {
            return;
        }
        if (VIEW_INCLUDE.equals(tag)) {
            return;
        }
        if (TAG_MERGE.equals(tag) || VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout children don't need to specify layout_width/layout_height
        // Also GridLayout itself needs them, but its children don't
        // Actually: GridLayout as a parent is the special case - children of GridLayout
        // don't need layout_width/layout_height. But the spec says GridLayout itself
        // doesn't require you to specify a size.
        // Re-reading: "GridLayout, as a special case, does not require you to specify a size."
        // This means views inside a GridLayout don't need it.

        // Check if this is the root element or if parent is GridLayout
        Node parentNode = element.getParentNode();
        if (parentNode != null && parentNode.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parentNode;
            String parentTag = parentElement.getTagName();
            if (isGridLayout(parentTag)) {
                return;
            }
        }

        // Root element of layout also needs layout_width and layout_height
        // (unless it's a merge)

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if a style provides the missing attributes
        String styleValue = element.getAttribute(ATTR_STYLE);
        if (styleValue != null && !styleValue.isEmpty()) {
            // Normalize style name
            String styleName = normalizeStyleName(styleValue);

            if (!hasWidth || !hasHeight) {
                // We need to check the style - but style files may not be parsed yet
                // We'll defer this check
                if (mPendingChecks == null) {
                    mPendingChecks = new ArrayList<>();
                }
                mPendingChecks.add(new PendingCheck(context, element, styleName));
                return;
            }
        }

        // No style reference, report immediately for missing attributes
        reportMissingAttributes(context, element, hasWidth, hasHeight);
    }

    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }

    @NonNull
    private static String normalizeStyleName(@NonNull String styleValue) {
        if (styleValue.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleValue.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleValue.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return styleValue.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        return styleValue;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingChecks == null) {
            return;
        }

        // Initialize cache sets
        if (mWidthStyles == null) {
            mWidthStyles = new HashSet<>();
        }
        if (mHeightStyles == null) {
            mHeightStyles = new HashSet<>();
        }
        if (mNoWidthStyles == null) {
            mNoWidthStyles = new HashSet<>();
        }
        if (mNoHeightStyles == null) {
            mNoHeightStyles = new HashSet<>();
        }

        for (PendingCheck check : mPendingChecks) {
            Element element = check.element;
            String styleName = check.styleName;

            boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            if (!hasWidth) {
                hasWidth = styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH, new HashSet<>());
            }
            if (!hasHeight) {
                hasHeight = styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT, new HashSet<>());
            }

            if (!hasWidth || !hasHeight) {
                reportMissingAttributes(check.context, element, hasWidth, hasHeight);
            }
        }
    }

    /**
     * Checks whether the given style (or any of its parents) defines the given attribute.
     */
    private boolean styleDefinesAttribute(
            @NonNull String styleName,
            @NonNull String attribute,
            @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        // Check cache
        if (attribute.equals(ATTR_LAYOUT_WIDTH)) {
            if (mWidthStyles != null && mWidthStyles.contains(styleName)) {
                return true;
            }
            if (mNoWidthStyles != null && mNoWidthStyles.contains(styleName)) {
                return false;
            }
        } else if (attribute.equals(ATTR_LAYOUT_HEIGHT)) {
            if (mHeightStyles != null && mHeightStyles.contains(styleName)) {
                return true;
            }
            if (mNoHeightStyles != null && mNoHeightStyles.contains(styleName)) {
                return false;
            }
        }

        boolean found = false;

        if (mStyleToAttrMap != null) {
            Set<String> attrs = mStyleToAttrMap.get(styleName);
            if (attrs != null && attrs.contains(attribute)) {
                found = true;
            }
        }

        if (!found && mStyleParentMap != null) {
            String parent = mStyleParentMap.get(styleName);
            if (parent != null) {
                found = styleDefinesAttribute(parent, attribute, visited);
            }
        }

        // Update caches
        if (found) {
            if (attribute.equals(ATTR_LAYOUT_WIDTH)) {
                if (mWidthStyles != null) mWidthStyles.add(styleName);
            } else {
                if (mHeightStyles != null) mHeightStyles.add(styleName);
            }
        } else {
            if (attribute.equals(ATTR_LAYOUT_WIDTH)) {
                if (mNoWidthStyles != null) mNoWidthStyles.add(styleName);
            } else {
                if (mNoHeightStyles != null) mNoHeightStyles.add(styleName);
            }
        }

        return found;
    }

    private static void reportMissingAttributes(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean hasWidth,
            boolean hasHeight) {
        String tag = element.getTagName();

        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "The `<%1$s>` element does not supply required attributes "
                                    + "`{android:layout_width}` and `{android:layout_height}`",
                            tag));
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "The `<%1$s>` element does not supply required attribute "
                                    + "`{android:layout_width}`",
                            tag));
        } else {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "The `<%1$s>` element does not supply required attribute "
                                    + "`{android:layout_height}`",
                            tag));
        }
    }
}