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
 * Checks whether a LinearLayout with weights has baseline alignment turned off.
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
        // Only consider horizontal LinearLayouts (or those without orientation set,
        // which defaults to horizontal)
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            // Vertical LinearLayouts don't do baseline alignment
            return;
        }

        // Check if baselineAligned is already set to false
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
            if ("false".equals(baselineAligned)) {
                // Already disabled, no issue
                return;
            }
        }

        // Check if any child has a layout_weight and is a nested layout (ViewGroup)
        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            // Check if child has layout_weight
            if (childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                // Check if this child is a layout/ViewGroup (nested layout)
                if (isLayout(childElement.getTagName())) {
                    hasWeightedChildLayout = true;
                    break;
                }
            }
        }

        if (hasWeightedChildLayout) {
            Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(attribute != null ? (Node) attribute : (Node) element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    /**
     * Returns true if the given tag name represents a layout (ViewGroup) container.
     */
    private static boolean isLayout(String tagName) {
        // Common layout types
        switch (tagName) {
            case LINEAR_LAYOUT:
            case "RelativeLayout":
            case "FrameLayout":
            case "TableLayout":
            case "TableRow":
            case "GridLayout":
            case "android.widget.LinearLayout":
            case "android.widget.RelativeLayout":
            case "android.widget.FrameLayout":
            case "android.widget.TableLayout":
            case "android.widget.GridLayout":
            case "androidx.constraintlayout.widget.ConstraintLayout":
            case "androidx.coordinatorlayout.widget.CoordinatorLayout":
            case "android.support.constraint.ConstraintLayout":
            case "android.support.design.widget.CoordinatorLayout":
            case "ScrollView":
            case "HorizontalScrollView":
            case "ListView":
            case "GridView":
            case "ViewGroup":
            case "merge":
                return true;
            default:
                // Heuristic: if the tag name ends with "Layout", treat it as a layout
                return tagName.endsWith("Layout");
        }
    }
}