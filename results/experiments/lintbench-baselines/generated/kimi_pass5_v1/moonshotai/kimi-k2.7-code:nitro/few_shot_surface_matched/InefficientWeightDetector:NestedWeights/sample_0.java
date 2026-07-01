package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with "
                            + "non-zero weights is nested inside another LinearLayout with non-zero "
                            + "weights, then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!hasWeight(element)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName()) && hasWeight(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Nested layout weights");
                return;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasWeight(org.w3c.dom.Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (value != null && !value.isEmpty()) {
            try {
                return Float.parseFloat(value) != 0f;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }
}