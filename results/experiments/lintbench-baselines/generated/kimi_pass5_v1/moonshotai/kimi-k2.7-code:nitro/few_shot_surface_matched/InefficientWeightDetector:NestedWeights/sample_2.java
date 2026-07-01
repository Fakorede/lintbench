package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class InefficientWeightDetector extends LayoutDetector implements Detector.XmlScanner {

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
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String TAG_LINEAR_LAYOUT_FQCN = "android.widget.LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(TAG_LINEAR_LAYOUT, TAG_LINEAR_LAYOUT_FQCN);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!isLinearLayout(element) || !usesWeights(element)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
            if (isLinearLayout(parentElement) && usesWeights(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Nested layout weights are bad for performance");
                break;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean isLinearLayout(org.w3c.dom.Element element) {
        String tag = element.getTagName();
        return TAG_LINEAR_LAYOUT.equals(tag) || TAG_LINEAR_LAYOUT_FQCN.equals(tag);
    }

    private static boolean usesWeights(org.w3c.dom.Element element) {
        if (hasNonZeroLayoutWeight(element)) {
            return true;
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
                if (hasNonZeroLayoutWeight(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNonZeroLayoutWeight(org.w3c.dom.Element element) {
        String value = element.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(ATTR_LAYOUT_WEIGHT);
        }
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(value) != 0.0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}