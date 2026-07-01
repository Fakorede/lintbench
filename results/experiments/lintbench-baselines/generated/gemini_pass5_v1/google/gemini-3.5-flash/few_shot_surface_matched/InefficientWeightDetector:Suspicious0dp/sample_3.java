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
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                            + "used when sizing the children. However, if you use 0dp for the opposite "
                            + "dimension, the view will be invisible. This can happen if you change the "
                            + "orientation of a layout without also flipping the `0dp` dimension in all "
                            + "the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.LAYOUT_RESOURCE_FILES));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                checkChild(context, child, isVertical);
            }
        }
    }

    private void checkChild(XmlContext context, Element child, boolean isVertical) {
        if (!child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return;
        }

        String suspiciousAttr = isVertical ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
        Attr attr = child.getAttributeNodeNS(ANDROID_URI, suspiciousAttr);
        if (attr != null) {
            String value = attr.getValue();
            if (isZeroDp(value)) {
                String message;
                if (isVertical) {
                    message = "Suspicious `layout_width=\"0dp\"` in `vertical` `LinearLayout` (did you mean `layout_height=\"0dp\"`?)";
                } else {
                    message = "Suspicious `layout_height=\"0dp\"` in `horizontal` `LinearLayout` (did you mean `layout_width=\"0dp\"`?)";
                }
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null) {
            return false;
        }
        String val = value.trim();
        if (val.startsWith("0")) {
            String unit = val.substring(1).trim();
            return unit.isEmpty()
                    || unit.equals("dp")
                    || unit.equals("dip")
                    || unit.equals("px")
                    || unit.equals("sp")
                    || unit.equals("in")
                    || unit.equals("mm")
                    || unit.equals("pt");
        }
        return false;
    }
}