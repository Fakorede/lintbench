package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;

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
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String weight = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return;
        }

        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return;
        }

        Element parentElement = (Element) parent;
        String parentTag = parentElement.getTagName();
        if (!parentTag.equals(SdkConstants.LINEAR_LAYOUT) && !parentTag.endsWith("." + SdkConstants.LINEAR_LAYOUT)) {
            return;
        }

        String orientation = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        String width = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        String height = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        boolean isZeroWidth = isZeroDp(width);
        boolean isZeroHeight = isZeroDp(height);

        if (isVertical) {
            if (isZeroWidth) {
                context.report(ISSUE, element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)),
                    "Suspicious 0dp width in a vertical LinearLayout; should probably be 0dp height instead");
            }
        } else {
            if (isZeroHeight) {
                context.report(ISSUE, element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)),
                    "Suspicious 0dp height in a horizontal LinearLayout; should probably be 0dp width instead");
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null) return false;
        value = value.trim();
        return value.equals("0dp") || value.equals("0dip");
    }
}