package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with"
                            + " non-zero weights is nested inside another LinearLayout with non-zero"
                            + " weights, then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.LAYOUT_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            if (hasDescendantWithWeight(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)),
                        "Nested weights are bad for performance");
            }
        }
    }

    private boolean hasDescendantWithWeight(org.w3c.dom.Node node) {
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
                if (childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    return true;
                }
                if (hasDescendantWithWeight(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }
}