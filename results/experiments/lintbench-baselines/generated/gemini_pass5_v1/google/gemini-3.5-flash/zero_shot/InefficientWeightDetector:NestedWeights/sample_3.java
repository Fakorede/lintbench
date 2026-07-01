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

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue NESTED_WEIGHTS = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.LINEAR_LAYOUT,
                "androidx.appcompat.widget.LinearLayoutCompat"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasWeights(element)) {
            Node parent = element.getParentNode();
            while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                String tagName = parentElement.getTagName();
                if (isLinearLayout(tagName)) {
                    if (hasWeights(parentElement)) {
                        context.report(
                                NESTED_WEIGHTS,
                                element,
                                context.getNameLocation(element),
                                "Nested weights are bad for performance"
                        );
                        break;
                    }
                }
                parent = parent.getParentNode();
            }
        }
    }

    private boolean isLinearLayout(String tagName) {
        return tagName.equals(SdkConstants.LINEAR_LAYOUT)
                || tagName.equals("androidx.appcompat.widget.LinearLayoutCompat")
                || tagName.endsWith(".LinearLayout")
                || tagName.endsWith(".LinearLayoutCompat");
    }

    private boolean hasWeights(Element linearLayout) {
        Node child = linearLayout.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String value = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT).trim();
                    if (!value.isEmpty() && !value.equals("0") && !value.equals("0.0")) {
                        return true;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }
}