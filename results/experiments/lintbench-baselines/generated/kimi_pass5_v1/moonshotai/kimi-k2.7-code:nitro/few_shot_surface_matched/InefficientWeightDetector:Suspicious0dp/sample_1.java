package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful"
                            + " trick to ensure that only the weights are used when sizing the"
                            + " children. However, if you use 0dp for the opposite dimension, the"
                            + " view will be invisible. This can happen if you change the"
                            + " orientation of a layout without also flipping the 0dp dimension"
                            + " in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String orientation = element.getAttribute("android:orientation");
        boolean isHorizontal = !"vertical".equals(orientation);

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element child = (org.w3c.dom.Element) node;

            if (isHorizontal) {
                org.w3c.dom.Attr attr = child.getAttributeNode("android:layout_height");
                if (attr != null && isZeroDimension(attr.getValue())) {
                    context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "Suspicious 0dp value: the height is 0dp in a horizontal"
                                    + " LinearLayout, making the view invisible");
                }
            } else {
                org.w3c.dom.Attr attr = child.getAttributeNode("android:layout_width");
                if (attr != null && isZeroDimension(attr.getValue())) {
                    context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "Suspicious 0dp value: the width is 0dp in a vertical"
                                    + " LinearLayout, making the view invisible");
                }
            }
        }
    }

    private static boolean isZeroDimension(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}