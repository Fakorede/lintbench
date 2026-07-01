package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

public class InefficientWeightDetector extends LayoutDetector {

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
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        float weight = getWeight(element);
        if (weight <= 0.0f) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                float parentWeight = getWeight(parentElement);
                if (parentWeight > 0.0f) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Nested weights are bad for performance");
                }
            }
        }
    }

    private static float getWeight(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (value.isEmpty() || value.startsWith("@")) {
            return 0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}