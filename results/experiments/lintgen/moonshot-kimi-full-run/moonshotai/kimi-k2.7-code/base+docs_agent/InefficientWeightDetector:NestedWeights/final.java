package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

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
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        if (!hasWeightedChildren(element)) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        if (!TAG_LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }

        if (hasWeightedChildren(parent)) {
            context.report(
                    NESTED_WEIGHTS,
                    element,
                    context.getLocation(element),
                    "Nested layout weights are inefficient");
        }
    }

    private static boolean hasWeightedChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String weight = ((Element) child).getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            try {
                if (Float.parseFloat(weight) != 0f) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Ignore invalid values.
            }
        }
        return false;
    }
}