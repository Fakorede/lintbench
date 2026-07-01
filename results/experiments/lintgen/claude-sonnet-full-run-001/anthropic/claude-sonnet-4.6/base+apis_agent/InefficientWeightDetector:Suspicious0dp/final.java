package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.*;

public class InefficientWeightDetector extends Detector implements XmlScanner {

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
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String VALUE_ZERO_DP = "0dp";
    private static final String VALUE_ZERO_DIP = "0dip";
    private static final String VALUE_ZERO_PX = "0px";

    public InefficientWeightDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Determine orientation of the LinearLayout
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isHorizontal = VALUE_HORIZONTAL.equals(orientation) || orientation == null || orientation.isEmpty();
        // Default orientation is horizontal if not specified
        // Actually default is horizontal
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        // Check each child of the LinearLayout
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if the child has a layout_weight attribute
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            // Get layout_width and layout_height
            String layoutWidth = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String layoutHeight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            boolean widthIsZero = isZeroDp(layoutWidth);
            boolean heightIsZero = isZeroDp(layoutHeight);

            if (isHorizontal && heightIsZero) {
                // In a horizontal LinearLayout, using 0dp for height makes the view invisible
                Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (heightAttr != null) {
                    context.report(ISSUE, childElement, context.getLocation(heightAttr),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_weight`; did you mean to use `0dp` for `layout_width` instead?");
                } else {
                    context.report(ISSUE, childElement, context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_weight`; did you mean to use `0dp` for `layout_width` instead?");
                }
            } else if (isVertical && widthIsZero) {
                // In a vertical LinearLayout, using 0dp for width makes the view invisible
                Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (widthAttr != null) {
                    context.report(ISSUE, childElement, context.getLocation(widthAttr),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_weight`; did you mean to use `0dp` for `layout_height` instead?");
                } else {
                    context.report(ISSUE, childElement, context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be " +
                            "used with `layout_weight`; did you mean to use `0dp` for `layout_height` instead?");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null) {
            return false;
        }
        return VALUE_ZERO_DP.equals(value) || VALUE_ZERO_DIP.equals(value) || VALUE_ZERO_PX.equals(value);
    }
}