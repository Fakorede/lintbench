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
 * Checks whether a layout_weight is nested inside another layout_weight.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Nested weights issue */
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
        // Check if this LinearLayout has a layout_weight attribute itself,
        // meaning it's a child of another weighted LinearLayout.
        // We need to check: does this LinearLayout have a layout_weight set,
        // AND does it contain children with layout_weight set?

        // First, check if this LinearLayout itself has a non-zero layout_weight
        // (meaning it's nested inside another LinearLayout with weights)
        boolean hasWeight = hasNonZeroWeight(element);

        if (hasWeight) {
            // Check if any direct children also have layout_weight set
            // (meaning this LinearLayout itself uses weights)
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (hasNonZeroWeight(childElement)) {
                        // This LinearLayout has a weight AND contains children with weights
                        // Report on the layout_weight attribute of this LinearLayout
                        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        if (weightAttr != null) {
                            context.report(
                                    NESTED_WEIGHTS,
                                    weightAttr,
                                    context.getLocation(weightAttr),
                                    "Nested weights are bad for performance");
                        }
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
        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr != null) {
            String value = weightAttr.getValue();
            if (value != null && !value.isEmpty()) {
                try {
                    float weight = Float.parseFloat(value);
                    return weight != 0f;
                } catch (NumberFormatException e) {
                    // If we can't parse it, assume it's non-zero
                    return true;
                }
            }
        }
        return false;
    }
}