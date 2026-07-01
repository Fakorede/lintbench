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
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
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
 * In particular, layout_width and layout_height are required for all views.
 */
public class RequiredAttributeDetector extends Detector implements XmlScanner {

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
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from style name (fully qualified, e.g. "@style/Foo") to set of
     * attribute names defined in that style (e.g. "layout_width").
     */
    @Nullable
    private Map<String, Set<String>> mStyleToAttrMap;

    /**
     * Map from style name to parent style name (used to resolve inheritance).
     */
    @Nullable
    private Map<String, String> mStyleParentMap;

    /**
     * Set of style names that have been fully resolved (i.e. we've walked the
     * inheritance chain and merged all attributes).
     */
    @Nullable
    private Set<String> mResolvedStyles;

    /**
     * List of pending layout elements that need to be checked after all styles
     * have been collected. Each entry is a pair of (XmlContext, Element).
     */
    @Nullable
    private List<PendingCheck> mPendingChecks;

    // ---- Constructors ----

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    // ---- Static utility method required by tests ----

    /**
     * Returns whether the given layout file has layout variations (i.e. there
     * exist other layout folders that contain a file with the same name).
     *
     * @param file the layout file to check
     * @return true if there are layout variations
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
        File[] folders = resFolder.listFiles();
        if (folders == null) {
            return false;
        }
        int count = 0;
        for (File folder : folders) {
            String folderName = folder.getName();
            if (folderName.startsWith("layout") && !folder.equals(parent)) {
                File variation = new File(folder, fileName);
                if (variation.exists()) {
                    count++;
                    if (count > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Nothing to do here; processing is done in visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            // Collect style definitions
            collectStyles(context, element);
        } else if (folderType == ResourceFolderType.LAYOUT) {
            // Check layout elements for required attributes
            checkLayoutElement(context, element);
        }
    }

    /**
     * Collects style definitions from a values XML file element.
     */
    private void collectStyles(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_STYLE.equals(tagName)) {
            // <style name="Foo" parent="Bar">
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName == null || styleName.isEmpty()) {
                return;
            }

            // Normalize style name to @style/Foo format
            String qualifiedName = STYLE_RESOURCE_PREFIX + styleName;

            Set<String> attrs = new HashSet<>();
            if (mStyleToAttrMap == null) {
                mStyleToAttrMap = new HashMap<>();
            }
            mStyleToAttrMap.put(qualifiedName, attrs);

            // Record parent
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                if (mStyleParentMap == null) {
                    mStyleParentMap = new HashMap<>();
                }
                // Normalize parent reference
                parent = normalizeStyleRef(parent);
                mStyleParentMap.put(qualifiedName, parent);
            } else {
                // Check for implicit parent via dot notation (e.g. "Foo.Bar" -> parent is "Foo")
                int dotIndex = styleName.lastIndexOf('.');
                if (dotIndex > 0) {
                    String implicitParent = STYLE_RESOURCE_PREFIX + styleName.substring(0, dotIndex);
                    if (mStyleParentMap == null) {
                        mStyleParentMap = new HashMap<>();
                    }
                    mStyleParentMap.put(qualifiedName, implicitParent);
                }
            }

            // Collect items within this style
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (itemName != null) {
                            // Strip android: prefix if present
                            if (itemName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                                itemName = itemName.substring(ANDROID_NS_NAME_PREFIX.length());
                            }
                            attrs.add(itemName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Normalizes a style reference to the @style/Foo format.
     */
    private static String normalizeStyleRef(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style;
        } else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return style; // keep as-is for framework styles
        } else if (style.startsWith("@")) {
            return style;
        } else {
            return STYLE_RESOURCE_PREFIX + style;
        }
    }

    /**
     * Checks a layout element for required layout_width and layout_height attributes.
     */
    private void checkLayoutElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // <merge> and <include> are special cases
        if (VIEW_MERGE.equals(tag)) {
            return;
        }

        // GridLayout does not require layout_width/layout_height
        if (isGridLayout(tag)) {
            return;
        }

        // <include> tag: the included layout should have its own attributes
        if (VIEW_INCLUDE.equals(tag)) {
            return;
        }

        // Check for layout_width and layout_height
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the style provides these attributes
        String style = element.getAttribute(ATTR_STYLE);
        if (style != null && !style.isEmpty()) {
            // We need to defer this check until after all styles are collected
            if (mPendingChecks == null) {
                mPendingChecks = new ArrayList<>();
            }
            mPendingChecks.add(new PendingCheck(context, element, style, hasWidth, hasHeight));
            return;
        }

        // No style, report missing attributes
        reportMissing(context, element, hasWidth, hasHeight);
    }

    /**
     * Reports missing layout_width and/or layout_height attributes.
     */
    private static void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            boolean hasWidth,
            boolean hasHeight) {
        if (!hasWidth && !hasHeight) {
            String message = "The required `layout_width` and `layout_height` attributes "
                    + "are missing";
            context.report(ISSUE, element, context.getLocation(element), message);
        } else if (!hasWidth) {
            String message = "The required `layout_width` attribute is missing";
            context.report(ISSUE, element, context.getLocation(element), message);
        } else {
            String message = "The required `layout_height` attribute is missing";
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    /**
     * Returns true if the given tag represents a GridLayout (which doesn't require
     * explicit layout_width/layout_height).
     */
    private static boolean isGridLayout(@NonNull String tag) {
        return GRID_LAYOUT.equals(tag)
                || tag.endsWith(".GridLayout");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now that all styles have been collected, process pending checks
        if (mPendingChecks != null) {
            for (PendingCheck check : mPendingChecks) {
                boolean hasWidth = check.hasWidth;
                boolean hasHeight = check.hasHeight;

                if (!hasWidth || !hasHeight) {
                    // Try to resolve the style
                    Set<String> styleAttrs = resolveStyle(check.style);
                    if (styleAttrs != null) {
                        if (!hasWidth) {
                            hasWidth = styleAttrs.contains(ATTR_LAYOUT_WIDTH);
                        }
                        if (!hasHeight) {
                            hasHeight = styleAttrs.contains(ATTR_LAYOUT_HEIGHT);
                        }
                    }
                }

                if (!hasWidth || !hasHeight) {
                    reportMissing(check.context, check.element, hasWidth, hasHeight);
                }
            }
        }
    }

    /**
     * Resolves a style reference and returns the set of all attribute names
     * defined in that style (including inherited attributes).
     */
    @Nullable
    private Set<String> resolveStyle(@NonNull String styleRef) {
        if (mStyleToAttrMap == null) {
            return null;
        }

        String normalized = normalizeStyleRef(styleRef);

        // Check if already resolved
        if (mResolvedStyles != null && mResolvedStyles.contains(normalized)) {
            return mStyleToAttrMap.get(normalized);
        }

        Set<String> attrs = mStyleToAttrMap.get(normalized);
        if (attrs == null) {
            // Style not found (could be a framework style or external style)
            // For framework styles, we can't know what they contain, so assume they
            // might have the required attributes
            if (normalized.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
                return Collections.emptySet(); // Unknown, don't assume
            }
            return null;
        }

        // Resolve parent styles
        if (mStyleParentMap != null) {
            String parentRef = mStyleParentMap.get(normalized);
            if (parentRef != null) {
                Set<String> parentAttrs = resolveStyle(parentRef);
                if (parentAttrs != null && !parentAttrs.isEmpty()) {
                    // Merge parent attributes into this style's set
                    Set<String> merged = new HashSet<>(attrs);
                    merged.addAll(parentAttrs);
                    attrs = merged;
                    mStyleToAttrMap.put(normalized, attrs);
                }
            }
        }

        if (mResolvedStyles == null) {
            mResolvedStyles = new HashSet<>();
        }
        mResolvedStyles.add(normalized);

        return attrs;
    }

    /**
     * Holds information about a pending layout check that needs style resolution.
     */
    private static class PendingCheck {
        @NonNull final XmlContext context;
        @NonNull final Element element;
        @NonNull final String style;
        final boolean hasWidth;
        final boolean hasHeight;

        PendingCheck(
                @NonNull XmlContext context,
                @NonNull Element element,
                @NonNull String style,
                boolean hasWidth,
                boolean hasHeight) {
            this.context = context;
            this.element = element;
            this.style = style;
            this.hasWidth = hasWidth;
            this.hasHeight = hasHeight;
        }
    }
}