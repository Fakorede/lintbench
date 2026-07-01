package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;

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
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                    + "non-zero weights is nested inside another `LinearLayout` with non-zero "
                    + "weights, then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasWeightedChildren(element)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_LINEAR_LAYOUT.equals(childElement.getTagName())
                    && hasWeightedChildren(childElement)) {
                context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Nested layout weights are bad for performance");
            }
        }
    }

    private static boolean hasWeightedChildren(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && hasNonZeroWeight((Element) child)) {
                return true;
            }
        }
        return false;
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
            return Double.parseDouble(value) != 0.0;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}