package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with " +
                    "non-zero weights is nested inside another LinearLayout with non-zero weights, " +
                    "then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight.isEmpty()) {
            return;
        }

        float weightValue;
        try {
            weightValue = Float.parseFloat(weight);
        } catch (NumberFormatException e) {
            return;
        }

        if (weightValue <= 0.0f) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                String parentWeight = parentElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (!parentWeight.isEmpty()) {
                    try {
                        float parentWeightValue = Float.parseFloat(parentWeight);
                        if (parentWeightValue > 0.0f) {
                            context.report(ISSUE, element, context.getLocation(element),
                                    "Nested weights are bad for performance");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        // Ignore malformed parent weight
                    }
                }
                // Only the immediate LinearLayout parent matters for this performance characteristic
                break;
            }
            parent = parent.getParentNode();
        }
    }
}