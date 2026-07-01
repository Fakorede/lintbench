package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children.\n\n"
                            + "However, if you use 0dp for the opposite dimension, the view will be invisible. "
                            + "This can happen if you change the orientation of a layout without also flipping "
                            + "the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.LAYOUT_SCOPE));

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
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                Attr weightAttr = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_weight");
                if (weightAttr != null) {
                    if (isVertical) {
                        Attr widthAttr = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_width");
                        if (widthAttr != null && isZero(widthAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    widthAttr,
                                    context.getLocation(widthAttr),
                                    "Suspicious size: this will make the view invisible, "
                                            + "probably intended for `layout_height` instead?");
                        }
                    } else {
                        Attr heightAttr = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_height");
                        if (heightAttr != null && isZero(heightAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    heightAttr,
                                    context.getLocation(heightAttr),
                                    "Suspicious size: this will make the view invisible, "
                                            + "probably intended for `layout_width` instead?");
                        }
                    }
                }
            }
        }
    }

    private static boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.equals("0")) {
            return true;
        }
        if (trimmed.startsWith("0")) {
            if (trimmed.length() > 1) {
                char second = trimmed.charAt(1);
                return !Character.isDigit(second) && second != '.';
            }
        }
        return false;
    }
}