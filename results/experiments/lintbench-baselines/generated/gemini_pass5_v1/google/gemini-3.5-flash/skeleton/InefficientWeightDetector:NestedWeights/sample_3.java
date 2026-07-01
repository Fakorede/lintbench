package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                            + "non-zero weights is nested inside another `LinearLayout` with non-zero weights, "
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
        if (!"LinearLayout".equals(element.getTagName())) {
            return;
        }

        if (hasWeights(element)) {
            Element ancestor = findAncestorWithWeights(element);
            if (ancestor != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Nested layout weights can hinder performance");
            }
        }
    }

    private boolean hasWeights(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    String value = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                    if (isNonZero(value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNonZero(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            double val = Double.parseDouble(value.trim());
            return val > 0.0;
        } catch (NumberFormatException e) {
            return !value.equals("0") && !value.equals("0.0");
        }
    }

    private Element findAncestorWithWeights(Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if ("LinearLayout".equals(parentElement.getTagName())) {
                if (hasWeights(parentElement)) {
                    return parentElement;
                }
            }
            parent = parentElement.getParentNode();
        }
        return null;
    }
}