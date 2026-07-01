package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) "
                            + "are used when sizing the children. However, if you use 0dp for the "
                            + "opposite dimension, the view will be invisible. This can happen if "
                            + "you change the orientation of a layout without also flipping the "
                            + "0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean horizontal =
                orientation.isEmpty()
                        || "horizontal".equals(orientation)
                        || !"vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            if (!hasPositiveWeight(child)) {
                continue;
            }

            if (horizontal) {
                if (isZeroDp(child, ATTR_LAYOUT_HEIGHT)) {
                    reportSuspicious0dp(
                            context,
                            child,
                            ATTR_LAYOUT_HEIGHT,
                            "layout_height",
                            "horizontal");
                }
            } else {
                if (isZeroDp(child, ATTR_LAYOUT_WIDTH)) {
                    reportSuspicious0dp(
                            context,
                            child,
                            ATTR_LAYOUT_WIDTH,
                            "layout_width",
                            "vertical");
                }
            }
        }
    }

    private static boolean hasPositiveWeight(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isZeroDp(Element element, String attributeName) {
        String value = element.getAttributeNS(ANDROID_URI, attributeName);
        return value != null && (value.equals("0dp") || value.equals("0dip"));
    }

    private static void reportSuspicious0dp(
            XmlContext context,
            Element element,
            String attributeName,
            String dimensionName,
            String orientation) {
        String message =
                String.format(
                        "Suspicious 0dp dimension: %1$s is 0dp but the LinearLayout orientation is %2$s",
                        dimensionName,
                        orientation);
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, attributeName);
        Location location = attr != null ? context.getLocation(attr) : context.getLocation(element);
        context.report(ISSUE, element, location, message);
    }
}