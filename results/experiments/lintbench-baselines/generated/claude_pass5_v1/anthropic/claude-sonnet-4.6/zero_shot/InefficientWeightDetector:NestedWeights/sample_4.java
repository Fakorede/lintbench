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

    /** The main issue discovered by this detector */
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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has any children with a layout_weight attribute
        boolean hasWeight = false;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    String weightValue = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                    if (weightValue != null && !weightValue.isEmpty() && !weightValue.equals("0")) {
                        hasWeight = true;
                        break;
                    }
                }
            }
        }

        if (!hasWeight) {
            return;
        }

        // Check if any ancestor LinearLayout also has weights applied to its children
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getLocalName()) ||
                    LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                // Check if this LinearLayout child (i.e., the element we started with
                // or an intermediate node) has a layout_weight
                // We need to check if the direct child of this ancestor LinearLayout
                // that contains our element has a weight
                Node directChild = findDirectChild(parentElement, element);
                if (directChild instanceof Element) {
                    Element directChildElement = (Element) directChild;
                    if (directChildElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                        String weightValue = directChildElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        if (weightValue != null && !weightValue.isEmpty() && !weightValue.equals("0")) {
                            // Found nested weights - report on the layout_weight attribute of the current element
                            // Actually report on the current element's children's weight attributes
                            reportNestedWeights(context, element);
                            return;
                        }
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }

    /**
     * Finds the direct child of {@code ancestor} that is or contains {@code descendant}.
     */
    private static Node findDirectChild(@NonNull Element ancestor, @NonNull Element descendant) {
        Node current = descendant;
        while (current != null) {
            Node p = current.getParentNode();
            if (p == ancestor) {
                return current;
            }
            current = p;
        }
        return null;
    }

    /**
     * Reports the nested weights issue on the weight attributes of the children of the given element.
     */
    private static void reportNestedWeights(@NonNull XmlContext context, @NonNull Element element) {
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    String weightValue = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                    if (weightValue != null && !weightValue.isEmpty() && !weightValue.equals("0")) {
                        Attr weightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        context.report(NESTED_WEIGHTS, weightAttr,
                                context.getLocation(weightAttr),
                                "Nested weights are bad for performance");
                    }
                }
            }
        }
    }
}