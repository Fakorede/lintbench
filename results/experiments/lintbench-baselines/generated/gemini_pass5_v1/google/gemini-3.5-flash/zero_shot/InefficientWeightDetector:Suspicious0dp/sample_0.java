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

    public static final Issue SUSPICIOUS_0DP = Issue.create(
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
            Severity.WARNING,
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
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String oppositeDimensionAttr = isVertical ? SdkConstants.ATTR_LAYOUT_WIDTH : SdkConstants.ATTR_LAYOUT_HEIGHT;
                    Attr oppositeAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, oppositeDimensionAttr);
                    if (oppositeAttr != null) {
                        String value = oppositeAttr.getValue();
                        if (isZero(value)) {
                            String message = String.format(
                                    "Suspicious size: this will make the View invisible in a %s layout",
                                    isVertical ? "vertical" : "horizontal"
                            );
                            context.report(SUSPICIOUS_0DP, oppositeAttr, context.getLocation(oppositeAttr), message);
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
        String s = value.trim();
        if (s.equals("0")) {
            return true;
        }
        if (s.startsWith("0")) {
            int i = 1;
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i < s.length()) {
                String units = s.substring(i).trim();
                return units.equals("dp") || units.equals("dip") || units.equals("px")
                        || units.equals("sp") || units.equals("in") || units.equals("mm")
                        || units.equals("pt");
            }
        }
        return false;
    }
}