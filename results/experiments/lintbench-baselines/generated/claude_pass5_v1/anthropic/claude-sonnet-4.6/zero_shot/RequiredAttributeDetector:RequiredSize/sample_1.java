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
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
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
 * In particular, layout_width and layout_height are required for most views.
 */
public class RequiredAttributeDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n" +
            "\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Set of style names (fully qualified, e.g. "@style/foo") that provide
     * layout_width and/or layout_height
     */
    private Map<String, Boolean> mStylesWithWidth;
    private Map<String, Boolean> mStylesWithHeight;

    /**
     * Map from style name to parent style name
     */
    private Map<String, String> mStyleParents;

    /**
     * Pending locations to report errors for - we stash these during the file scan
     * and report them after we've had a chance to check all the style files.
     */
    private List<PendingError> mPendingErrors;

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
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
        // For values files, we scan for style definitions
        if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            scanValueFile(context, document);
        }
    }

    private void scanValueFile(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
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

    private void processStyleElement(@NonNull Element style) {
        String name = style.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Record parent
        String parent = style.getAttribute("parent");
        if (parent != null && !parent.isEmpty()) {
            if (mStyleParents == null) {
                mStyleParents = new HashMap<>();
            }
            mStyleParents.put(name, parent);
        } else {
            // Check for implicit parent via dot notation
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex != -1) {
                String implicitParent = name.substring(0, dotIndex);
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(name, implicitParent);
            }
        }

        // Check if this style defines layout_width or layout_height
        boolean hasWidth = false;
        boolean hasHeight = false;

        NodeList items = style.getChildNodes();
        for (int i = 0, n = items.getLength(); i < n; i++) {
            Node item = items.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element itemElement = (Element) item;
                if ("item".equals(itemElement.getTagName())) {
                    String itemName = itemElement.getAttribute("name");
                    if (ATTR_LAYOUT_WIDTH.equals(itemName)
                            || (ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH).equals(itemName)) {
                        hasWidth = true;
                    } else if (ATTR_LAYOUT_HEIGHT.equals(itemName)
                            || (ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT).equals(itemName)) {
                        hasHeight = true;
                    }
                }
            }
        }

        if (hasWidth) {
            if (mStylesWithWidth == null) {
                mStylesWithWidth = new HashMap<>();
            }
            mStylesWithWidth.put(name, Boolean.TRUE);
        }

        if (hasHeight) {
            if (mStylesWithHeight == null) {
                mStylesWithHeight = new HashMap<>();
            }
            mStylesWithHeight.put(name, Boolean.TRUE);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();

        // The root element doesn't need layout params
        if (element.getParentNode() == element.getOwnerDocument()) {
            return;
        }

        // <merge> tags don't need layout params
        if (TAG_MERGE.equals(tag) || VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout doesn't require layout_width and layout_height
        if (GRID_LAYOUT.equals(tag) || tag.endsWith(".GridLayout")) {
            return;
        }

        // Check if the tag is a <requestFocus> or similar non-view element
        if ("requestFocus".equals(tag) || "tag".equals(tag)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the style attribute provides width/height
        if (!hasWidth || !hasHeight) {
            String style = element.getAttribute(ATTR_STYLE);
            if (style != null && !style.isEmpty()) {
                // We need to defer this check until after all style files are processed
                if (mPendingErrors == null) {
                    mPendingErrors = new ArrayList<>();
                }
                Location location = context.getLocation(element);
                mPendingErrors.add(new PendingError(
                        context.getProject(),
                        location,
                        tag,
                        style,
                        hasWidth,
                        hasHeight));
                return;
            }
        }

        // Report the error immediately
        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The view must specify `layout_width` and `layout_height` attributes");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The view must specify `layout_width`");
        } else {
            context.report(ISSUE, element, context.getLocation(element),
                    "The view must specify `layout_height`");
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingErrors != null) {
            for (PendingError error : mPendingErrors) {
                String style = error.style;
                boolean hasWidth = error.hasWidth || styleDefinesAttribute(style, true);
                boolean hasHeight = error.hasHeight || styleDefinesAttribute(style, false);

                if (!hasWidth && !hasHeight) {
                    context.report(ISSUE, error.location,
                            "The view must specify `layout_width` and `layout_height` attributes");
                } else if (!hasWidth) {
                    context.report(ISSUE, error.location,
                            "The view must specify `layout_width`");
                } else if (!hasHeight) {
                    context.report(ISSUE, error.location,
                            "The view must specify `layout_height`");
                }
            }
        }
    }

    /**
     * Checks whether a given style (or one of its parents) defines layout_width or layout_height.
     *
     * @param styleRef the style reference (e.g. "@style/foo" or just "foo")
     * @param checkWidth if true, check for layout_width; otherwise check for layout_height
     * @return true if the style defines the requested attribute
     */
    private boolean styleDefinesAttribute(@NonNull String styleRef, boolean checkWidth) {
        // Normalize the style reference
        String styleName = normalizeStyleName(styleRef);
        if (styleName == null) {
            return false;
        }

        // Avoid infinite loops
        Set<String> visited = new HashSet<>();
        return styleDefinesAttributeRecursive(styleName, checkWidth, visited);
    }

    private boolean styleDefinesAttributeRecursive(
            @NonNull String styleName,
            boolean checkWidth,
            @NonNull Set<String> visited) {
        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        Map<String, Boolean> map = checkWidth ? mStylesWithWidth : mStylesWithHeight;
        if (map != null && map.containsKey(styleName)) {
            return true;
        }

        // Check parent style
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                String parentName = normalizeStyleName(parent);
                if (parentName != null) {
                    return styleDefinesAttributeRecursive(parentName, checkWidth, visited);
                }
            }
        }

        return false;
    }

    @Nullable
    private static String normalizeStyleName(@NonNull String styleRef) {
        if (styleRef.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleRef.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleRef.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            // Android framework styles - we don't have access to those
            return null;
        }
        // Already a plain name
        return styleRef;
    }

    /** Represents a pending error to be reported after all files have been scanned */
    private static class PendingError {
        public final Project project;
        public final Location location;
        public final String tag;
        public final String style;
        public final boolean hasWidth;
        public final boolean hasHeight;

        PendingError(
                @NonNull Project project,
                @NonNull Location location,
                @NonNull String tag,
                @NonNull String style,
                boolean hasWidth,
                boolean hasHeight) {
            this.project = project;
            this.location = location;
            this.tag = tag;
            this.style = style;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }
}