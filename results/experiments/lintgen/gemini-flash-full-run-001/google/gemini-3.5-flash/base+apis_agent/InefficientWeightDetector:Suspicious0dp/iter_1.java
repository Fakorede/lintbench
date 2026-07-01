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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = "vertical".equals(orientation);

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            if (isVertical) {
                String width = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                if (isZero(width)) {
                    Attr widthAttr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
                    if (widthAttr != null) {
                        context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                                "Suspicious size: this will make the view invisible in a vertical layout");
                    }
                }
            } else {
                String height = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                if (isZero(height)) {
                    Attr heightAttr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
                    if (heightAttr != null) {
                        context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                                "Suspicious size: this will make the view invisible in a horizontal layout");
                    }
                }
            }
        }
    }

    private static boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        if (value.equals("0") || value.equals("0dp") || value.equals("0dip") || value.equals("0px")) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("0")) {
            return trimmed.matches("0+(?:px|dp|dip|sp|in|mm|pt)?");
        }
        return false;
    }
}