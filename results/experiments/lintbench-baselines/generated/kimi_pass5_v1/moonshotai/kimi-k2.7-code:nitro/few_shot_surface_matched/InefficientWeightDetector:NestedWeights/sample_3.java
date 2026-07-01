package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with"
                            + " non-zero weights is nested inside another LinearLayout with"
                            + " non-zero weights, then the number of measurements increase"
                            + " exponentially.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        if (!TAG_LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }
        if (hasWeightedChild(parent) && hasWeightedChild(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Nested layout weights are inefficient");
        }
    }

    private static boolean hasWeightedChild(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasNonZeroWeight(childElement)) {
                    return true;
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private static boolean hasNonZeroWeight(Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
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