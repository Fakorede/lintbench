package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

import java.util.Arrays;
import java.util.Collection;

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

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Determine orientation of the LinearLayout
        String orientation = element.getAttributeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_ORIENTATION);

        // Default orientation is horizontal
        boolean isHorizontal = !SdkConstants.VALUE_VERTICAL.equals(orientation);

        // Check children for suspicious 0dp usage
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Only check children that have a weight set
            String weight = childElement.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            String width = childElement.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_LAYOUT_WIDTH);
            String height = childElement.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_LAYOUT_HEIGHT);

            boolean widthIsZero = is0dp(width);
            boolean heightIsZero = is0dp(height);

            if (isHorizontal) {
                // In a horizontal layout, 0dp width is correct (weight-based sizing)
                // But 0dp height is suspicious (view will be invisible)
                if (heightIsZero) {
                    Attr heightAttr = childElement.getAttributeNodeNS(
                            SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_LAYOUT_HEIGHT);
                    context.report(
                            ISSUE,
                            childElement,
                            heightAttr != null ? context.getLocation(heightAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be used with " +
                            "`layout_width` instead (the layout is horizontal)"
                    );
                }
            } else {
                // In a vertical layout, 0dp height is correct (weight-based sizing)
                // But 0dp width is suspicious (view will be invisible)
                if (widthIsZero) {
                    Attr widthAttr = childElement.getAttributeNodeNS(
                            SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_LAYOUT_WIDTH);
                    context.report(
                            ISSUE,
                            childElement,
                            widthAttr != null ? context.getLocation(widthAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be used with " +
                            "`layout_height` instead (the layout is vertical)"
                    );
                }
            }
        }
    }

    private static boolean is0dp(String dimension) {
        if (dimension == null) {
            return false;
        }
        String trimmed = dimension.trim();
        return trimmed.equals("0dp") || trimmed.equals("0dip") || trimmed.equals("0px")
                || trimmed.equals("0") || trimmed.equals("0.0dp") || trimmed.equals("0.0dip");
    }
}