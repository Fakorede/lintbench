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
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for inefficient weight usage in nested LinearLayouts.
 */
public class InefficientWeightDetector extends LayoutDetector {

    /** Nested layout weights */
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
        // Check if this LinearLayout has any children with a layout_weight attribute
        if (!hasChildWithWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has children with weights
        if (isNestedInWeightedLinearLayout(element)) {
            context.report(
                    NESTED_WEIGHTS,
                    element,
                    context.getLocation(element),
                    "Nested weights are bad for performance");
        }
    }

    /**
     * Returns true if the given LinearLayout has at least one direct child
     * with a non-zero layout_weight attribute.
     */
    private static boolean hasChildWithWeight(@NonNull Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        float weightValue = Float.parseFloat(weight);
                        if (weightValue != 0) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // If we can't parse it, assume it's non-zero
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given element is nested inside a LinearLayout that
     * itself has children with non-zero weights.
     */
    private static boolean isNestedInWeightedLinearLayout(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            String tagName = parentElement.getTagName();
            if (LINEAR_LAYOUT.equals(tagName)) {
                if (hasChildWithWeight(parentElement)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}