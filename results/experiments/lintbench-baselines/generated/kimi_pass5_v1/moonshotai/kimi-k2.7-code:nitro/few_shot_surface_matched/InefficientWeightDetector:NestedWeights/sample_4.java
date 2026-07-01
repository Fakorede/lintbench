package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout"
                            + " with non-zero weights is nested inside another LinearLayout with"
                            + " non-zero weights, the number of measurements increase"
                            + " exponentially.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasNonZeroWeight(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName())
                    && hasNonZeroWeight(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Nested layout weights are inefficient due to repeated measure passes");
                return;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasNonZeroWeight(Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(value.trim()) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}