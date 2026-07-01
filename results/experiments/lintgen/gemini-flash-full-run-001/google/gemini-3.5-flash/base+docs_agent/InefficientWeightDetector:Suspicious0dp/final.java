package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.LINEAR_LAYOUT,
                "androidx.appcompat.widget.LinearLayoutCompat",
                "android.support.v7.widget.LinearLayoutCompat"
        );
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
                    if (isVertical) {
                        Attr widthAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                        if (widthAttr != null && isZeroDimension(widthAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    widthAttr,
                                    context.getLocation(widthAttr),
                                    "Suspicious size: this will make the view invisible in a vertical layout"
                            );
                        }
                    } else {
                        Attr heightAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                        if (heightAttr != null && isZeroDimension(heightAttr.getValue())) {
                            context.report(
                                    ISSUE,
                                    heightAttr,
                                    context.getLocation(heightAttr),
                                    "Suspicious size: this will make the view invisible in a horizontal layout"
                            );
                        }
                    }
                }
            }
        }
    }

    private static boolean isZeroDimension(String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            String rest = value.substring(1).trim();
            return rest.equals("dp") || rest.equals("dip") || rest.equals("px")
                    || rest.equals("sp") || rest.equals("in") || rest.equals("mm")
                    || rest.equals("pt");
        }
        if (value.startsWith("0.0")) {
            String rest = value.substring(3).trim();
            return rest.equals("dp") || rest.equals("dip") || rest.equals("px")
                    || rest.equals("sp") || rest.equals("in") || rest.equals("mm")
                    || rest.equals("pt") || rest.isEmpty();
        }
        return false;
    }
}