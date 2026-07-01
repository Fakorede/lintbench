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
 * Checks whether a layout has nested LinearLayouts with weights, which is
 * expensive due to the double measurement passes.
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
        // Check if this LinearLayout has a weight attribute (meaning it is a child
        // of another LinearLayout with weights)
        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr != null) {
            String weightValue = weightAttr.getValue();
            if (weightValue != null && !weightValue.isEmpty() && !weightValue.equals("0")) {
                // This LinearLayout itself has a non-zero weight.
                // Now check if it also has children with non-zero weights.
                if (hasChildrenWithWeight(element)) {
                    context.report(
                            NESTED_WEIGHTS,
                            element,
                            context.getLocation(element),
                            "Nested weights are bad for performance");
                }
            }
        }
    }

    /**
     * Returns true if the given LinearLayout element has at least one direct
     * child with a non-zero layout_weight attribute.
     */
    private static boolean hasChildrenWithWeight(@NonNull Element linearLayout) {
        Node child = linearLayout.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr childWeight = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (childWeight != null) {
                    String value = childWeight.getValue();
                    if (value != null && !value.isEmpty() && !value.equals("0")) {
                        return true;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }
}