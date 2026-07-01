package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isVertical) {
                    Attr widthAttr = childElement.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_width");
                    if (widthAttr != null && isZero(widthAttr.getValue())) {
                        context.report(WRONG_0DP, widthAttr, context.getLocation(widthAttr),
                                "Suspicious size: this will make the view invisible in a vertical layout");
                    }
                } else {
                    // Horizontal is the default orientation for LinearLayout
                    Attr heightAttr = childElement.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_height");
                    if (heightAttr != null && isZero(heightAttr.getValue())) {
                        context.report(WRONG_0DP, heightAttr, context.getLocation(heightAttr),
                                "Suspicious size: this will make the view invisible in a horizontal layout");
                    }
                }
            }
        }
    }

    private static boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            return value.matches("^0+(?:\\.0+)?\\s*(?:dp|dip|px|sp|in|mm|pt)$");
        }
        return false;
    }
}