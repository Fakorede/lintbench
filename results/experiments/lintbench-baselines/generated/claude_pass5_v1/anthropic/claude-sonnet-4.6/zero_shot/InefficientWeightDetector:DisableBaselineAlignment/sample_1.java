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
 * Checks whether a LinearLayout which uses weight-based distribution contains
 * nested layouts and whether baselineAligned is properly set to false for
 * performance.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Issue: Missing baselineAligned attribute in weighted LinearLayout */
    public static final Issue INEFFICIENT_WEIGHT = Issue.create(
            "InefficientWeight",
            "Inefficient layout weight",
            "When only a single widget in a `LinearLayout` defines a weight, it is more "
                    + "efficient to assign a width/height of `0dp` to that widget. This way "
                    + "there is no need to measure the dimension, and the weight alone determines "
                    + "the size.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Issue: Missing baselineAligned="false" in weighted LinearLayout */
    public static final Issue DISABLE_BASELINE_ALIGNED = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between "
                    + "nested layouts, the baseline alignment property should be turned off to "
                    + "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Issue: Baseline aligned children in horizontal LinearLayout */
    public static final Issue BASELINE_WEIGHTS = Issue.create(
            "BaselineWeights",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between "
                    + "nested layouts, the baseline alignment property should be turned off to "
                    + "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Issue: Wrong 0dp dimension */
    public static final Issue WRONG_0DP = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                    + "used when sizing the children.\n"
                    + "\n"
                    + "However, if the orientation is **vertical**, then a 0dp width is "
                    + "suspicious and will likely result in invisible children. Similarly, "
                    + "a 0dp height in a horizontal `LinearLayout` is suspicious.",
            Category.PERFORMANCE,
            6,
            Severity.ERROR,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check LinearLayout elements
        if (!LINEAR_LAYOUT.equals(element.getTagName())
                && !LINEAR_LAYOUT.equals(element.getLocalName())) {
            return;
        }

        // Check if baselineAligned is already set to false
        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        boolean isBaselineAligned = !("false".equals(baselineAligned));

        // Determine orientation
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        // If it's vertical, baseline alignment doesn't apply (only horizontal)
        if (isVertical) {
            return;
        }

        // Check if any child has a layout_weight attribute and is a layout (ViewGroup)
        if (!isBaselineAligned) {
            // Already disabled, no issue
            return;
        }

        // Check children: does any child have layout_weight and is a nested layout?
        NodeList children = element.getChildNodes();
        boolean hasWeightedNestedLayout = false;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String layoutWeight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (layoutWeight != null && !layoutWeight.isEmpty()) {
                // Check if this child is a layout (ViewGroup) - i.e., a container
                String childTag = childElement.getTagName();
                if (isLayout(childTag)) {
                    hasWeightedNestedLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedNestedLayout) {
            context.report(
                    DISABLE_BASELINE_ALIGNED,
                    element,
                    context.getLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    /**
     * Returns true if the given tag name represents a layout container (ViewGroup).
     */
    private static boolean isLayout(String tag) {
        if (tag == null) {
            return false;
        }
        // Check for common layout containers
        switch (tag) {
            case "LinearLayout":
            case "RelativeLayout":
            case "FrameLayout":
            case "TableLayout":
            case "TableRow":
            case "GridLayout":
            case "ConstraintLayout":
            case "CoordinatorLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "ViewGroup":
            case "merge":
            case "include":
            case "RadioGroup":
            case "GridView":
            case "ListView":
            case "ExpandableListView":
            case "AbsoluteLayout":
            case "android.support.constraint.ConstraintLayout":
            case "androidx.constraintlayout.widget.ConstraintLayout":
            case "android.support.design.widget.CoordinatorLayout":
            case "androidx.coordinatorlayout.widget.CoordinatorLayout":
                return true;
            default:
                // Heuristic: if the tag ends with "Layout" it's likely a layout container
                return tag.endsWith("Layout") || tag.endsWith("layout");
        }
    }
}