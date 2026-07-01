package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout "
                            + "with non-zero weights is nested inside another LinearLayout with "
                            + "non-zero weights, the number of measurements increases "
                            + "exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasWeight(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if (LINEAR_LAYOUT.equals(parent.getNodeName()) && hasWeight((Element) parent)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Nested layout weights are bad for performance");
                return;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasWeight(Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
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