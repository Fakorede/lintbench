package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "NestedWeights",
        "Nested layout weights",
        "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
        "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
        "then the number of measurements increase exponentially.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String weight = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight.isEmpty() || isZero(weight)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (SdkConstants.LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                    String parentWeight = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (!parentWeight.isEmpty() && !isZero(parentWeight)) {
                        context.report(ISSUE, element, context.getLocation(element),
                            "Nested weights are bad for performance");
                        return;
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean isZero(String value) {
        try {
            return Float.parseFloat(value) == 0.0f;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}