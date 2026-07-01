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
 * Checks whether a LinearLayout which distributes space proportionally using
 * layout_weight has baselineAligned turned off for performance.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Issue for missing baselineAligned attribute */
    public static final Issue ISSUE = Issue.create(
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

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has baselineAligned explicitly set to false
        // If so, no issue.
        Attr baselineAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (baselineAttr != null) {
            // The attribute is explicitly set; no problem.
            return;
        }

        // Check orientation: baseline alignment only matters for horizontal LinearLayouts.
        // For vertical LinearLayouts, baseline alignment is not relevant.
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        // Check if any child has a layout_weight attribute AND is itself a layout (ViewGroup)
        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if the child has layout_weight
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight != null && !weight.isEmpty()) {
                // Check if the child is a layout (i.e., a ViewGroup / container)
                if (isLayout(childElement)) {
                    hasWeightedChildLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedChildLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for performance " +
                    "if you do not need to align the baselines of the children");
        }
    }

    /**
     * Returns true if the given element is a layout container (ViewGroup).
     */
    private static boolean isLayout(@NonNull Element element) {
        String tag = element.getTagName();
        // Check common layout names
        if (tag.endsWith("Layout")) {
            return true;
        }
        switch (tag) {
            case "ScrollView":
            case "HorizontalScrollView":
            case "ListView":
            case "GridView":
            case "ExpandableListView":
            case "ViewPager":
            case "ViewAnimator":
            case "ViewFlipper":
            case "ViewSwitcher":
            case "TextSwitcher":
            case "ImageSwitcher":
            case "AdapterViewFlipper":
            case "SlidingDrawer":
            case "TabHost":
            case "TabWidget":
            case "Spinner":
            case "android.widget.ScrollView":
            case "android.widget.ListView":
            case "android.widget.GridView":
                return true;
            default:
                // If the tag contains a dot, it's a custom view; we can't be sure,
                // but we'll treat it as a potential layout if it ends with common suffixes.
                if (tag.contains(".")) {
                    return tag.endsWith("Layout") ||
                           tag.endsWith("Container") ||
                           tag.endsWith("Group") ||
                           tag.endsWith("View"); // conservative: skip custom views
                }
                return false;
        }
    }
}