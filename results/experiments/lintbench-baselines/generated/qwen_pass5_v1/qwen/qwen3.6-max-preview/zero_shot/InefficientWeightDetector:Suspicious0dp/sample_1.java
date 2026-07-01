package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful trick to ensure that only the weights (and not the intrinsic sizes) are used when sizing the children.\n\n" +
            "However, if you use 0dp for the opposite dimension, the view will be invisible. This can happen if you change the orientation of a layout without also flipping the 0dp dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) node, isVertical);
            }
        }
    }

    private void checkChild(XmlContext context, Element child, boolean isVertical) {
        Attr weightAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        String weightValue = weightAttr.getValue();
        try {
            float weight = Float.parseFloat(weightValue);
            if (weight <= 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            // Weight is likely a resource reference; skip static analysis
            return;
        }

        String crossAxisAttrName = isVertical ? SdkConstants.ATTR_LAYOUT_WIDTH : SdkConstants.ATTR_LAYOUT_HEIGHT;
        Attr crossAxisAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, crossAxisAttrName);

        if (crossAxisAttr != null) {
            String value = crossAxisAttr.getValue();
            if ("0dp".equals(value) || "0dip".equals(value)) {
                context.report(ISSUE, context.getLocation(crossAxisAttr),
                        "Suspicious 0dp dimension on the non-weighted axis");
            }
        }
    }
}