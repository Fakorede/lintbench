package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

    public static final Issue NESTED_WEIGHTS = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` " +
            "with non-zero weights is nested inside another `LinearLayout` with non-zero " +
            "weights, then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        if (!(element.getParentNode() instanceof Element)) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        if (!SdkConstants.TAG_LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }

        if (hasNonZeroWeight(element) && hasNonZeroWeight(parent)) {
            context.report(
                    NESTED_WEIGHTS,
                    element,
                    context.getLocation(element),
                    "Nested layout weights are inefficient");
        }
    }

    private static boolean hasNonZeroWeight(Element element) {
        String weight = element.getAttributeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }

        try {
            return Float.parseFloat(weight) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}