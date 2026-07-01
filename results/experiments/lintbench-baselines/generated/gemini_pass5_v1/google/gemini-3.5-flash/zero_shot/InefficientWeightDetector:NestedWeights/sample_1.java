package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.LAYOUT_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.LINEAR_LAYOUT,
                "android.support.v7.widget.LinearLayoutCompat",
                "androidx.appcompat.widget.LinearLayoutCompat"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasWeights(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (isLinearLayout(parentElement) && hasWeights(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Nested weights are bad for performance"
                );
                break;
            }
            parent = parent.getParentNode();
        }
    }

    private boolean isLinearLayout(Element element) {
        String tagName = element.getTagName();
        return tagName.equals(SdkConstants.LINEAR_LAYOUT)
                || tagName.equals("android.support.v7.widget.LinearLayoutCompat")
                || tagName.equals("androidx.appcompat.widget.LinearLayoutCompat");
    }

    private boolean hasWeights(Element linearLayout) {
        NodeList nodeList = linearLayout.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String weight = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (!weight.isEmpty() && !weight.equals("0") && !weight.equals("0dp") && !weight.equals("0.0")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}