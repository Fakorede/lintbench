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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

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
                    new Implementation(
                            InefficientWeightDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasWeight(element)) {
            Node parent = element.getParentNode();
            while (parent != null) {
                if (parent.getNodeType() == Node.ELEMENT_NODE
                        && parent.getNodeName().equals(LINEAR_LAYOUT)) {
                    if (hasWeight((Element) parent)) {
                        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        if (weightAttr != null) {
                            context.report(ISSUE, weightAttr, context.getLocation(weightAttr),
                                    "Nested weights are bad for performance");
                        } else {
                            context.report(ISSUE, element, context.getLocation(element),
                                    "Nested weights are bad for performance");
                        }
                        break;
                    }
                }
                parent = parent.getParentNode();
            }
        }
    }

    private boolean hasWeight(Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return true;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    return true;
                }
            }
        }
        return false;
    }
}