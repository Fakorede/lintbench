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
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;

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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks whether a LinearLayout with weights contains nested layouts without
 * baselineAligned set to false.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue DISABLE_BASELINE_ALIGNMENT = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Constructs a new {@link InefficientWeightDetector}.
     */
    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has baselineAligned explicitly set to false already
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            // Already disabled, no issue
            return;
        }

        // Determine the orientation of the LinearLayout
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        // Default orientation is horizontal if not specified
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        // For vertical LinearLayouts, baseline alignment doesn't apply
        // (baseline alignment is only relevant for horizontal LinearLayouts)
        if (isVertical) {
            return;
        }

        // Check if any child has a layout_weight attribute and is itself a layout
        boolean hasWeightedNestedLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if child has layout_weight
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            // Check if child is a layout (i.e., a ViewGroup / container)
            if (isLayoutElement(childElement)) {
                hasWeightedNestedLayout = true;
                break;
            }
        }

        if (hasWeightedNestedLayout) {
            // baselineAligned is not set to false, but there are nested layouts with weights
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
            if (attr != null) {
                context.report(
                        DISABLE_BASELINE_ALIGNMENT,
                        attr,
                        context.getLocation(attr),
                        "Set `android:baselineAligned=\"false\"` on this element for better performance");
            } else {
                context.report(
                        DISABLE_BASELINE_ALIGNMENT,
                        element,
                        context.getLocation(element),
                        "Set `android:baselineAligned=\"false\"` on this element for better performance");
            }
        }
    }

    /**
     * Returns true if the given element is a layout container (ViewGroup).
     */
    private static boolean isLayoutElement(@NonNull Element element) {
        String tag = element.getTagName();
        // Common layout containers
        return tag.equals("LinearLayout")
                || tag.equals("RelativeLayout")
                || tag.equals("FrameLayout")
                || tag.equals("TableLayout")
                || tag.equals("TableRow")
                || tag.equals("GridLayout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("ListView")
                || tag.equals("GridView")
                || tag.equals("ConstraintLayout")
                || tag.equals("CoordinatorLayout")
                || tag.equals("DrawerLayout")
                || tag.equals("ViewPager")
                || tag.equals("RecyclerView")
                || tag.equals("RadioGroup")
                || tag.equals("SlidingDrawer")
                || tag.equals("merge")
                || tag.equals("include")
                // Check for custom layouts by seeing if the tag contains a dot
                // (fully qualified class name) - heuristic for custom ViewGroups
                || tag.contains("Layout")
                || tag.contains(".")
                || tag.equals("android.widget.LinearLayout")
                || tag.equals("android.widget.RelativeLayout")
                || tag.equals("android.widget.FrameLayout");
    }
}