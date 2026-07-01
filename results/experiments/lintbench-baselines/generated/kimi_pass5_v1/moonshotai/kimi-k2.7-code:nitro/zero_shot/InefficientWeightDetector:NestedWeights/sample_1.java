package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

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

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with non-zero weights is nested inside another `LinearLayout` with non-zero weights, then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasNonZeroWeight(element)) {
            return;
        }

        Element parent = getParentElement(element);
        while (parent != null) {
            if (LINEAR_LAYOUT.equals(parent.getTagName()) && hasNonZeroWeight(parent)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Nested `layout_weight` attributes will result in exponential measurement overhead");
                return;
            }
            parent = getParentElement(parent);
        }
    }

    private static Element getParentElement(@NonNull Element element) {
        if (element.getParentNode() instanceof Element) {
            return (Element) element.getParentNode();
        }
        return null;
    }

    private static boolean hasNonZeroWeight(@NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return false;
        }

        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (value.startsWith("@")) {
            return true;
        }

        try {
            return Float.parseFloat(value) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}