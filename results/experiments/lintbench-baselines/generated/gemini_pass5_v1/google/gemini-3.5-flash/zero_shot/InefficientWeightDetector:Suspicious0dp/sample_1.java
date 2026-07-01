package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        for (Node childNode = element.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) childNode;

            if (isVertical) {
                Attr widthAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                if (widthAttr != null && isZeroDp(widthAttr.getValue())) {
                    Location location = context.getLocation(widthAttr);
                    context.report(ISSUE, child, location, "Suspicious size: this will make the view invisible in a vertical layout");
                }
            } else {
                Attr heightAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                if (heightAttr != null && isZeroDp(heightAttr.getValue())) {
                    Location location = context.getLocation(heightAttr);
                    context.report(ISSUE, child, location, "Suspicious size: this will make the view invisible in a horizontal layout");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null) {
            return false;
        }
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            String unit = value.substring(1).trim();
            return "dp".equals(unit) || "dip".equals(unit) || "px".equals(unit) 
                || "sp".equals(unit) || "in".equals(unit) || "mm".equals(unit);
        }
        return false;
    }
}