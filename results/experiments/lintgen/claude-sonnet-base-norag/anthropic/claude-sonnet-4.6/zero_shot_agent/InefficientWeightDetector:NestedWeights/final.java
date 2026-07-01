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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

/**
 * Checks whether a layout uses nested weights, which can be expensive.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Issue for nested layout weights */
    public static final Issue NESTED_WEIGHTS = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
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
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has a layout_weight attribute set on itself,
        // meaning it is a child of another LinearLayout with weights.
        // We need to check if this element has a non-zero layout_weight AND
        // contains children with non-zero layout_weight values.

        // First, check if this LinearLayout itself has a non-zero layout_weight
        // (meaning it's nested inside another LinearLayout with weights)
        boolean hasWeight = hasNonZeroWeight(element);

        if (hasWeight) {
            // Check if any children also have non-zero weights
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (hasNonZeroWeight(childElement)) {
                        // This LinearLayout has a weight and contains children with weights
                        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        context.report(
                                NESTED_WEIGHTS,
                                element,
                                context.getLocation(weightAttr != null ? weightAttr : (Node) element),
                                "Nested weights are bad for performance");
                        return;
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given element has a non-zero android:layout_weight attribute.
     */
    private static boolean hasNonZeroWeight(@NonNull Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight != null && !weight.isEmpty()) {
            try {
                float value = Float.parseFloat(weight);
                return value != 0f;
            } catch (NumberFormatException e) {
                // Not a valid float, treat as non-zero to be safe
                return true;
            }
        }
        return false;
    }
}