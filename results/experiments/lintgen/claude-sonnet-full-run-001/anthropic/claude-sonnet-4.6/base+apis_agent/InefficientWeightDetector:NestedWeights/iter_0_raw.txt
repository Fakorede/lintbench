package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.*;

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

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this LinearLayout has non-zero weights
        if (!hasNonZeroWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has non-zero weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName()) ||
                    parentElement.getTagName().endsWith(".LinearLayout")) {
                if (hasNonZeroWeight(parentElement)) {
                    context.report(
                            NESTED_WEIGHTS,
                            element,
                            context.getLocation(element),
                            "Nested weights are bad for performance"
                    );
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasNonZeroWeight(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
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
                        // Not a valid float, skip
                    }
                }
            }
        }
        return false;
    }
}