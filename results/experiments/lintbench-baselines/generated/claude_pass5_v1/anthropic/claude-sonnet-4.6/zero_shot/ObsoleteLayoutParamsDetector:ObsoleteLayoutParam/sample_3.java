/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_X;
import static com.android.SdkConstants.ATTR_LAYOUT_Y;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for obsolete layout params (layout params that are defined for a
 * layout type that is not the parent of the element containing the layout
 * param).
 */
public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from each layout_param attribute name to the set of layouts that
     * support it.
     */
    private static final Map<String, Set<String>> sParamToLayouts =
            new HashMap<String, Set<String>>();

    static {
        // RelativeLayout params
        Set<String> relativeParams = new HashSet<String>(Arrays.asList(
                RELATIVE_LAYOUT
        ));
        sParamToLayouts.put(ATTR_LAYOUT_ABOVE, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_BELOW, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_TO_LEFT_OF, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_TO_RIGHT_OF, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_TO_START_OF, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_TO_END_OF, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_LEFT, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_RIGHT, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_START, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_END, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_TOP, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_BOTTOM, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_BASELINE, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_LEFT, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_START, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_END, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_TOP, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_CENTER_HORIZONTAL, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_CENTER_VERTICAL, relativeParams);
        sParamToLayouts.put(ATTR_LAYOUT_CENTER_IN_PARENT, relativeParams);

        // LinearLayout params
        Set<String> linearParams = new HashSet<String>(Arrays.asList(
                LINEAR_LAYOUT, TABLE_LAYOUT, TABLE_ROW, RADIO_GROUP
        ));
        sParamToLayouts.put(ATTR_LAYOUT_WEIGHT, linearParams);

        // layout_gravity is supported in LinearLayout, FrameLayout, ScrollView, etc.
        Set<String> gravityParams = new HashSet<String>(Arrays.asList(
                LINEAR_LAYOUT, TABLE_LAYOUT, TABLE_ROW, FRAME_LAYOUT,
                SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW, RADIO_GROUP
        ));
        sParamToLayouts.put(ATTR_LAYOUT_GRAVITY, gravityParams);

        // TableRow / TableLayout params
        Set<String> tableParams = new HashSet<String>(Arrays.asList(
                TABLE_ROW
        ));
        sParamToLayouts.put(ATTR_LAYOUT_COLUMN, tableParams);
        sParamToLayouts.put(ATTR_LAYOUT_SPAN, tableParams);

        // GridLayout params
        Set<String> gridParams = new HashSet<String>(Arrays.asList(
                GRID_LAYOUT
        ));
        sParamToLayouts.put(ATTR_LAYOUT_ROW, gridParams);
        sParamToLayouts.put(ATTR_LAYOUT_ROW_SPAN, gridParams);
        sParamToLayouts.put(ATTR_LAYOUT_ROW_WEIGHT, gridParams);
        sParamToLayouts.put(ATTR_LAYOUT_COLUMN_SPAN, gridParams);
        sParamToLayouts.put(ATTR_LAYOUT_COLUMN_WEIGHT, gridParams);

        // AbsoluteLayout params
        Set<String> absoluteParams = new HashSet<String>(Arrays.asList(
                ABSOLUTE_LAYOUT
        ));
        sParamToLayouts.put(ATTR_LAYOUT_X, absoluteParams);
        sParamToLayouts.put(ATTR_LAYOUT_Y, absoluteParams);
    }

    // Layout names not defined as constants in SdkConstants
    private static final String FRAME_LAYOUT = "FrameLayout";
    private static final String SCROLL_VIEW = "ScrollView";
    private static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView";
    private static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";
    private static final String RADIO_GROUP = "RadioGroup";

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return sParamToLayouts.keySet();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            if (name != null && name.startsWith("layout_")) {
                // Strip namespace prefix if present
                int colon = name.indexOf(':');
                if (colon != -1) {
                    name = name.substring(colon + 1);
                }
            }
        }

        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        // Only check attributes in the android namespace
        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !namespace.equals(ANDROID_URI)) {
            return;
        }

        Set<String> validParents = sParamToLayouts.get(name);
        if (validParents == null) {
            return;
        }

        // Get the element that has this attribute
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Get the parent element (the layout that contains this view)
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Top-level element; no parent layout to check against
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Strip package prefix from parent tag if present (e.g., "android.widget.LinearLayout"
        // -> "LinearLayout")
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex != -1) {
            parentTag = parentTag.substring(dotIndex + 1);
        }

        // Check if the parent layout supports this layout param
        if (!validParents.contains(parentTag)) {
            String message = String.format(
                    "Invalid layout param '%1$s' (not defined for parent layout '%2$s')",
                    name, parentTag);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}