package com.android.tools.lint.checks;

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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_ZERO_DP;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                    + "when sizing the children.\n"
                    + "\n"
                    + "However, if you use 0dp for the opposite dimension, the view will be invisible. "
                    + "This can happen if you change the orientation of a layout without also flipping "
                    + "the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        // Default orientation is horizontal
        boolean isHorizontal = !VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            // Check if the child has a layout_weight
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            if (isHorizontal) {
                // In a horizontal LinearLayout, 0dp width is correct (weight-based sizing)
                // but 0dp height is suspicious (view will be invisible)
                if (VALUE_ZERO_DP.equals(height)) {
                    Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    context.report(ISSUE,
                            childElement,
                            heightAttr != null ? context.getLocation(heightAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be used with "
                                    + "`layout_width` instead (and a `layout_weight` is set)");
                }
            } else {
                // In a vertical LinearLayout, 0dp height is correct (weight-based sizing)
                // but 0dp width is suspicious (view will be invisible)
                if (VALUE_ZERO_DP.equals(width)) {
                    Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    context.report(ISSUE,
                            childElement,
                            widthAttr != null ? context.getLocation(widthAttr) : context.getLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be used with "
                                    + "`layout_height` instead (and a `layout_weight` is set)");
                }
            }
        }
    }
}