package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.*;

import java.util.*;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this LinearLayout has non-zero weights among its children
        if (!hasNonZeroWeightChild(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has non-zero weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName()) ||
                    LINEAR_LAYOUT.equals(getLocalName(parentElement))) {
                if (hasNonZeroWeightChild(parentElement)) {
                    // This LinearLayout with weights is nested inside another LinearLayout with weights
                    // Report on the layout_weight attribute of this element within its parent
                    // Find the weight attribute of this element
                    Attr weightAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
                    if (weightAttr == null) {
                        weightAttr = element.getAttributeNode("android:" + ATTR_LAYOUT_WEIGHT);
                    }

                    if (weightAttr != null) {
                        context.report(
                                NESTED_WEIGHTS,
                                weightAttr,
                                context.getLocation(weightAttr),
                                "Nested weights are bad for performance"
                        );
                    } else {
                        context.report(
                                NESTED_WEIGHTS,
                                element,
                                context.getLocation(element),
                                "Nested weights are bad for performance"
                        );
                    }
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasNonZeroWeightChild(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String weightValue = childElement.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
            if (weightValue == null || weightValue.isEmpty()) {
                // Try without namespace
                weightValue = childElement.getAttribute("android:layout_weight");
            }
            if (weightValue != null && !weightValue.isEmpty()) {
                try {
                    float weight = Float.parseFloat(weightValue);
                    if (weight != 0) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Not a valid float, skip
                }
            }
        }
        return false;
    }

    private String getLocalName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName;
        }
        String tagName = element.getTagName();
        int colon = tagName.indexOf(':');
        if (colon >= 0) {
            return tagName.substring(colon + 1);
        }
        return tagName;
    }
}