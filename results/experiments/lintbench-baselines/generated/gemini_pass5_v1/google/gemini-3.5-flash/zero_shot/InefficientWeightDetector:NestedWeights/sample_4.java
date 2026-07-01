package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        if (!hasWeights(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element ancestor = (Element) parent;
            if (SdkConstants.LINEAR_LAYOUT.equals(ancestor.getTagName())) {
                if (hasWeights(ancestor)) {
                    Element target = null;
                    for (Element child : getChildren(element)) {
                        if (hasLayoutWeight(child)) {
                            target = child;
                            break;
                        }
                    }
                    if (target == null) {
                        target = element;
                    }

                    Attr weightAttr = getLayoutWeightAttr(target);
                    Location location;
                    if (weightAttr != null) {
                        location = context.getLocation(weightAttr);
                    } else {
                        location = context.getLocation(target);
                    }

                    context.report(
                            NESTED_WEIGHTS,
                            location,
                            "Nested layout weights can hinder performance"
                    );
                    break;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasWeights(Element linearLayout) {
        for (Element child : getChildren(linearLayout)) {
            if (hasLayoutWeight(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLayoutWeight(Element element) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)
                || element.hasAttribute(SdkConstants.ATTR_LAYOUT_WEIGHT);
    }

    private static Attr getLayoutWeightAttr(Element element) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (attr == null) {
            attr = element.getAttributeNode(SdkConstants.ATTR_LAYOUT_WEIGHT);
        }
        return attr;
    }

    private static List<Element> getChildren(Node node) {
        List<Element> children = new ArrayList<>();
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) child);
            }
        }
        return children;
    }
}