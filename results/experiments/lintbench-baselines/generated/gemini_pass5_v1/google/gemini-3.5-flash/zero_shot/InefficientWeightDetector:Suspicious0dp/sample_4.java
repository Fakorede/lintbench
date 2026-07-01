package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue WRONG_0DP = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
            "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
            "when sizing the children.\n\n" +
            "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
            "This can happen if you change the orientation of a layout without also flipping " +
            "the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) childNode;

            if (isVertical) {
                Attr widthAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                if (widthAttr != null && isZero(widthAttr.getValue())) {
                    context.report(WRONG_0DP, widthAttr, context.getLocation(widthAttr),
                            "Suspicious size: this will make the view invisible in a vertical layout");
                }
            } else {
                Attr heightAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                if (heightAttr != null && isZero(heightAttr.getValue())) {
                    context.report(WRONG_0DP, heightAttr, context.getLocation(heightAttr),
                            "Suspicious size: this will make the view invisible in a horizontal layout");
                }
            }
        }
    }

    private static boolean isZero(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith("0")) {
            if (value.equals("0")) {
                return true;
            }
            String val = value.toLowerCase();
            return val.endsWith("dp") || val.endsWith("dip") || val.endsWith("px")
                    || val.endsWith("sp") || val.endsWith("pt") || val.endsWith("in")
                    || val.endsWith("mm");
        }
        return false;
    }
}