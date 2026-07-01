package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

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
                            Scope.LAYOUT_RESOURCE_FILES));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node instanceof org.w3c.dom.Element) {
                org.w3c.dom.Element child = (org.w3c.dom.Element) node;
                if (child.hasAttributeNS(ANDROID_URI, "layout_weight")) {
                    if (isVertical) {
                        org.w3c.dom.Attr widthAttr = child.getAttributeNodeNS(ANDROID_URI, "layout_width");
                        if (widthAttr != null && isZero(widthAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    widthAttr,
                                    context.getLocation(widthAttr),
                                    "Suspicious size: this will make the view invisible, should be layout_height instead?");
                        }
                    } else {
                        org.w3c.dom.Attr heightAttr = child.getAttributeNodeNS(ANDROID_URI, "layout_height");
                        if (heightAttr != null && isZero(heightAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    heightAttr,
                                    context.getLocation(heightAttr),
                                    "Suspicious size: this will make the view invisible, should be layout_width instead?");
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
        value = value.trim();
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            String units = value.substring(1).trim();
            return "dp".equals(units)
                    || "dip".equals(units)
                    || "px".equals(units)
                    || "sp".equals(units)
                    || "pt".equals(units)
                    || "in".equals(units)
                    || "mm".equals(units);
        }
        return false;
    }
}