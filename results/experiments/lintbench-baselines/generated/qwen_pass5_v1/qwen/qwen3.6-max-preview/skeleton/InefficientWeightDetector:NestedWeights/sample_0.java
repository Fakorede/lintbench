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

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with "
                    + "non-zero weights is nested inside another LinearLayout with non-zero weights, "
                    + "then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        float weight = getWeight(element);
        if (weight > 0.0f) {
            Node parent = element.getParentNode();
            if (parent instanceof Element) {
                Element parentElement = (Element) parent;
                if ("LinearLayout".equals(parentElement.getTagName()) && getWeight(parentElement) > 0.0f) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Nested weights are bad for performance");
                }
            }
        }
    }

    private static float getWeight(@NonNull Element element) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_weight");
        if (value == null || value.isEmpty()) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }
}