package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasWeight(element)) {
            Element parent = getParentLinearLayout(element);
            while (parent != null) {
                if (hasWeight(parent)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Nested weights are bad for performance");
                    break;
                }
                parent = getParentLinearLayout(parent);
            }
        }
    }

    private static boolean hasWeight(Element layout) {
        String weight = layout.getAttributeNS(SdkConstants.ANDROID_URI, "layout_weight");
        if (isPositiveWeight(weight)) {
            return true;
        }

        NodeList children = layout.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childWeight = ((Element) child).getAttributeNS(SdkConstants.ANDROID_URI, "layout_weight");
                if (isPositiveWeight(childWeight)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPositiveWeight(String weight) {
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) > 0.0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Element getParentLinearLayout(Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentEl = (Element) parent;
                String tag = parentEl.getTagName();
                if (tag.equals("LinearLayout") || tag.endsWith(":LinearLayout")) {
                    return parentEl;
                }
            }
            parent = parent.getParentNode();
        }
        return null;
    }
}