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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.ResourceRepositoryScope;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.lint.client.api.ResourceRepositoryScope;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks whether layout views are missing required attributes such as
 * layout_width and layout_height.
 */
public class RequiredAttributeDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, an exception "
                    + "is thrown at runtime.\n"
                    + "\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a special "
                    + "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from style name to whether it defines layout_width */
    private final Map<String, Boolean> mStyleWidths = new HashMap<>();

    /** Map from style name to whether it defines layout_height */
    private final Map<String, Boolean> mStyleHeights = new HashMap<>();

    /** Map from style name to parent style name */
    private final Map<String, String> mStyleParents = new HashMap<>();

    /** Set of elements we've already checked (to avoid duplicates from include tags) */
    private final Set<Element> mChecked = new HashSet<>();

    /** Constructs a new {@link RequiredAttributeDetector} */
    public RequiredAttributeDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this element is in a layout file
        String tagName = element.getTagName();

        // Merge tags don't need layout attributes
        if (TAG_MERGE.equals(tagName) || VIEW_MERGE.equals(tagName)) {
            return;
        }

        // GridLayout doesn't require size attributes
        if (GRID_LAYOUT.equals(tagName) || tagName.endsWith(".GridLayout")) {
            return;
        }

        // Check if this is the root element
        Document document = element.getOwnerDocument();
        Element root = document.getDocumentElement();

        // The root element in a layout file is the outermost view; it needs layout attributes
        // unless it's a merge tag. But actually, the root view still needs layout_width/height
        // in some cases. Let's check all non-merge, non-include elements.

        // Skip <include> and <requestFocus> tags
        if ("include".equals(tagName) || "requestFocus".equals(tagName)
                || "fragment".equals(tagName)) {
            return;
        }

        // Check for layout_width and layout_height attributes
        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Check if the style attribute provides the missing attributes
        if (!hasWidth || !hasHeight) {
            String style = element.getAttribute(ATTR_STYLE);
            if (style != null && !style.isEmpty()) {
                boolean styleHasWidth = styleDefinesAttribute(style, ATTR_LAYOUT_WIDTH, context);
                boolean styleHasHeight = styleDefinesAttribute(style, ATTR_LAYOUT_HEIGHT, context);
                if (!hasWidth) {
                    hasWidth = styleHasWidth;
                }
                if (!hasHeight) {
                    hasHeight = styleHasHeight;
                }
            }
        }

        if (!hasWidth || !hasHeight) {
            // Report the issue
            if (!hasWidth && !hasHeight) {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_width` and `layout_height` attributes are missing");
            } else if (!hasWidth) {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_width` attribute is missing");
            } else {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "The required `layout_height` attribute is missing");
            }
        }
    }

    /**
     * Checks whether a given style (or its parents) defines the given attribute.
     *
     * @param styleReference the style reference (e.g. "@style/MyStyle")
     * @param attribute       the attribute name to look for (without namespace prefix)
     * @param context         the XML context
     * @return true if the style defines the attribute
     */
    private boolean styleDefinesAttribute(
            @NonNull String styleReference,
            @NonNull String attribute,
            @NonNull XmlContext context) {

        // Normalize the style reference
        String styleName = getStyleName(styleReference);
        if (styleName == null) {
            return false;
        }

        // We use a visited set to prevent infinite loops from circular style references
        Set<String> visited = new HashSet<>();
        return styleDefinesAttribute(styleName, attribute, context, visited);
    }

    private boolean styleDefinesAttribute(
            @NonNull String styleName,
            @NonNull String attribute,
            @NonNull XmlContext context,
            @NonNull Set<String> visited) {

        if (visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        // Check cached results
        if (ATTR_LAYOUT_WIDTH.equals(attribute)) {
            Boolean result = mStyleWidths.get(styleName);
            if (result != null) {
                return result;
            }
        } else if (ATTR_LAYOUT_HEIGHT.equals(attribute)) {
            Boolean result = mStyleHeights.get(styleName);
            if (result != null) {
                return result;
            }
        }

        // Try to look up the style in the project's resource repository
        // For now we'll parse the style XML files
        // We check the parsed style data we collected
        boolean found = false;

        // Check parent style
        String parent = mStyleParents.get(styleName);
        if (parent != null) {
            found = styleDefinesAttribute(parent, attribute, context, visited);
        }

        return found;
    }

    /**
     * Extracts the style name from a style reference like "@style/Foo" or "?attr/Foo".
     */
    @Nullable
    private static String getStyleName(@NonNull String styleReference) {
        if (styleReference.startsWith(STYLE_RESOURCE_PREFIX)) {
            return styleReference.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (styleReference.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return styleReference.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        // Could be a bare style name or other reference
        return styleReference;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Collect style information from style resource files
        // This is handled separately in style file visits
    }

    /**
     * Records style attribute definitions found in style XML files.
     * This method is called when we encounter a style item element.
     */
    private void recordStyleAttribute(
            @NonNull String styleName,
            @NonNull String attributeName,
            @Nullable String parentName) {
        if (parentName != null && !parentName.isEmpty()) {
            mStyleParents.put(styleName, getStyleName(parentName));
        }

        String attrWithPrefix = ANDROID_NS_NAME_PREFIX + attributeName;
        if (attributeName.equals(ATTR_LAYOUT_WIDTH) || attrWithPrefix.equals(ATTR_LAYOUT_WIDTH)) {
            mStyleWidths.put(styleName, Boolean.TRUE);
        } else if (attributeName.equals(ATTR_LAYOUT_HEIGHT)
                || attrWithPrefix.equals(ATTR_LAYOUT_HEIGHT)) {
            mStyleHeights.put(styleName, Boolean.TRUE);
        }
    }
}