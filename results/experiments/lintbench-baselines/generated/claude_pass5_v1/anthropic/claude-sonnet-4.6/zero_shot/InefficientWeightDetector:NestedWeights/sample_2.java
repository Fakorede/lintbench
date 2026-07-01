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
 * Checks whether a layout uses weights in a nested manner, which leads to
 * exponential measurement passes.
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
        // Check if this LinearLayout itself has a layout_weight attribute,
        // meaning it is a child with weight inside another LinearLayout
        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        // Check that the weight value is non-zero
        String weightValue = weightAttr.getValue();
        if (weightValue == null || weightValue.isEmpty()) {
            return;
        }
        try {
            float weight = Float.parseFloat(weightValue);
            if (weight == 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            // If we can't parse it, assume it's non-zero
        }

        // Now check if any child of this LinearLayout also has a layout_weight
        if (hasChildWithWeight(element)) {
            context.report(NESTED_WEIGHTS, weightAttr, context.getLocation(weightAttr),
                    "Nested weights are bad for performance");
        }
    }

    /**
     * Returns true if the given element has at least one direct child element
     * with a non-zero android:layout_weight attribute.
     */
    private static boolean hasChildWithWeight(@NonNull Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr weightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weightAttr != null) {
                    String weightValue = weightAttr.getValue();
                    if (weightValue != null && !weightValue.isEmpty()) {
                        try {
                            float weight = Float.parseFloat(weightValue);
                            if (weight != 0f) {
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            // Assume non-zero
                            return true;
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }
}