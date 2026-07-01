package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a LinearLayout "
                    + "with non-zero weights is nested inside another LinearLayout with "
                    + "non-zero weights, the number of measurements increases exponentially.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.LINEAR_LAYOUT, SdkConstants.CLASS_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasNonZeroWeight(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (isLinearLayout(parentElement) && hasNonZeroWeight(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Nested layout weights will cause the views to be measured multiple times, "
                                + "which is expensive"
                );
                return;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean isLinearLayout(Element element) {
        String tag = element.getTagName();
        return SdkConstants.LINEAR_LAYOUT.equals(tag)
                || SdkConstants.CLASS_LINEAR_LAYOUT.equals(tag);
    }

    private static boolean hasNonZeroWeight(Element element) {
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (value == null || value.isEmpty()) {
            return false;
        }

        try {
            return Float.parseFloat(value) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}