package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                            + "non-zero weights is nested inside another `LinearLayout` with non-zero "
                            + "weights, then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(com.android.SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasWeight(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            String tagName = parentElement.getTagName();
            if (com.android.SdkConstants.LINEAR_LAYOUT.equals(tagName) || "LinearLayout".equals(tagName)) {
                if (hasWeight(parentElement)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Nested layout weights can hinder performance");
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasWeight(@NonNull Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String weight = childElement.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (!"0".equals(weight) && !"0.0".equals(weight)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}