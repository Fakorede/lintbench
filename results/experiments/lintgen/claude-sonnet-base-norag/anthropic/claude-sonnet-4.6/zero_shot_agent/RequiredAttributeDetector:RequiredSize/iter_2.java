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
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks whether layout elements are missing required attributes such as
 * layout_width and layout_height.
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
                    + "It's possible to specify these widths via styles as well. GridLayout, "
                    + "as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Tag name for merge elements */
    private static final String TAG_MERGE = "merge";

    /**
     * Map from style name to set of attribute names defined in that style.
     * This is used to check whether layout_width/layout_height are provided
     * via a style reference.
     */
    @Nullable
    private Map<String, Set<String>> mStyleToAttributes;

    /**
     * Map from style name to parent style name, used to resolve style
     * inheritance.
     */
    @Nullable
    private Map<String, String> mStyleParents;

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    /**
     * Returns true if the given layout file has layout variations (e.g. layout-land, layout-sw600dp).
     * This is a utility method used by tests.
     */
    public static boolean hasLayoutVariations(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        File resFolder = parent.getParentFile();
        if (resFolder == null) {
            return false;
        }
        String fileName = file.getName();
        String parentName = parent.getName();
        // The base folder name (e.g. "layout")
        String baseFolderName;
        int dashIndex = parentName.indexOf('-');
        if (dashIndex >= 0) {
            baseFolderName = parentName.substring(0, dashIndex);
        } else {
            baseFolderName = parentName;
        }

        File[] siblings = resFolder.listFiles();
        if (siblings == null) {
            return false;
        }
        for (File sibling : siblings) {
            if (sibling.equals(parent)) {
                continue;
            }
            String siblingName = sibling.getName();
            // Check if this sibling folder is a variation of the same base folder
            if (siblingName.equals(baseFolderName)
                    || siblingName.startsWith(baseFolderName + "-")) {
                // Check if the same file exists in that folder
                File variation = new File(sibling, fileName);
                if (variation.exists()) {
                    return true;
                }
            }
        }
        return false;
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
        // For values files, parse style definitions
        if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            parseStylesFromDocument(document);
        }
    }

    /**
     * Parse style definitions from a values document and populate the style maps.
     */
    private void parseStylesFromDocument(@NonNull Document document) {
        NodeList resources = document.getElementsByTagName("resources");
        if (resources.getLength() == 0) {
            return;
        }

        NodeList children = resources.item(0).getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tag = element.getTagName();
            if ("style".equals(tag)) {
                String styleName = element.getAttribute("name");
                if (styleName == null || styleName.isEmpty()) {
                    continue;
                }

                String parent = element.getAttribute("parent");

                if (mStyleToAttributes == null) {
                    mStyleToAttributes = new HashMap<>();
                }
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }

                Set<String> attributes = new HashSet<>();
                NodeList items = element.getChildNodes();
                for (int j = 0, m = items.getLength(); j < m; j++) {
                    Node item = items.item(j);
                    if (item.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element itemElement = (Element) item;
                    if ("item".equals(itemElement.getTagName())) {
                        String name = itemElement.getAttribute("name");
                        if (name != null) {
                            // Strip android: prefix for comparison
                            if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                                name = name.substring(ANDROID_NS_NAME_PREFIX.length());
                            }
                            attributes.add(name);
                        }
                    }
                }

                mStyleToAttributes.put(styleName, attributes);

                if (parent != null && !parent.isEmpty()) {
                    mStyleParents.put(styleName, parent);
                } else {
                    // Check for implicit parent via dot notation
                    int dotIndex = styleName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String implicitParent = styleName.substring(0, dotIndex);
                        mStyleParents.put(styleName, implicitParent);
                    }
                }
            }
        }
    }

    /**
     * Check whether a given style (by reference string) provides the given attribute.
     */
    private boolean styleDefinesAttribute(@NonNull String styleRef, @NonNull String attribute) {
        // Normalize the style reference
        String styleName = normalizeStyleRef(styleRef);
        if (styleName == null) {
            return false;
        }

        // Use a visited set to avoid infinite loops
        Set<String> visited = new HashSet<>();
        return styleDefinesAttributeRecursive(styleName, attribute, visited);
    }

    private boolean styleDefinesAttributeRecursive(
            @NonNull String styleName,
            @NonNull String attribute,
            @NonNull Set<String> visited) {
        if (!visited.add(styleName)) {
            return false;
        }

        if (mStyleToAttributes != null) {
            Set<String> attrs = mStyleToAttributes.get(styleName);
            if (attrs != null && attrs.contains(attribute)) {
                return true;
            }
        }

        // Check parent
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                String normalizedParent = normalizeStyleRef(parent);
                if (normalizedParent != null) {
                    return styleDefinesAttributeRecursive(normalizedParent, attribute, visited);
                }
            }
        }

        return false;
    }

    /**
     * Normalize a style reference to just the style name (without @style/ prefix etc.)
     */
    @Nullable
    private static String normalizeStyleRef(@NonNull String styleRef) {
        if (styleRef.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleRef.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleRef.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            // Android framework styles - we don't have info about these
            // but they might define layout params; we'll be conservative and return null
            return null;
        }
        // Could be just the name directly
        return styleRef;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check layout files
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // The root element doesn't need layout_width/layout_height if it's a merge
        if (TAG_MERGE.equals(tag) || VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout children don't require layout_width/layout_height
        // Check if the parent is a GridLayout
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

        // Check if a style provides the missing attributes
        String styleRef = element.getAttribute(ATTR_STYLE);
        if (styleRef != null && !styleRef.isEmpty()) {
            if (!hasWidth) {
                hasWidth = styleDefinesAttribute(styleRef, ATTR_LAYOUT_WIDTH);
            }
            if (!hasHeight) {
                hasHeight = styleDefinesAttribute(styleRef, ATTR_LAYOUT_HEIGHT);
            }
        }

        if (hasWidth && hasHeight) {
            return;
        }

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getElementLocation(element),
                    "The following required attributes are missing: "
                            + "`layout_width`, `layout_height`");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getElementLocation(element),
                    "The following required attributes are missing: `layout_width`");
        } else {
            context.report(ISSUE, element, context.getElementLocation(element),
                    "The following required attributes are missing: `layout_height`");
        }
    }

    /**
     * Returns true if the given tag name represents a GridLayout.
     */
    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.equals("android.widget.GridLayout")
                || tag.endsWith(".GridLayout");
    }
}