package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.annotations.NonNull;
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

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {
    private static final String HORIZONTAL = "horizontal";
    private static final String VERTICAL = "vertical";

    public static final Issue ISSUE_SUSPICIOUS_0DP = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful trick "
                    + "to ensure that only the weights (and not the intrinsic sizes) are used when "
                    + "sizing the children. However, if you use 0dp for the opposite dimension, "
                    + "the view will be invisible. This can happen if you change the orientation "
                    + "of a layout without also flipping the `0dp` dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean horizontal;
        if (orientation == null || orientation.isEmpty() || HORIZONTAL.equals(orientation)) {
            horizontal = true;
        } else if (VERTICAL.equals(orientation)) {
            horizontal = false;
        } else {
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasNonZeroWeight(childElement)) {
                    if (horizontal) {
                        Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                        if (heightAttr != null && isZeroDp(heightAttr.getValue())) {
                            context.report(ISSUE_SUSPICIOUS_0DP, heightAttr, context.getLocation(heightAttr),
                                    "Suspicious 0dp dimension: in a horizontal `LinearLayout` with weights, "
                                            + "the height should not be 0dp");
                        }
                    } else {
                        Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                        if (widthAttr != null && isZeroDp(widthAttr.getValue())) {
                            context.report(ISSUE_SUSPICIOUS_0DP, widthAttr, context.getLocation(widthAttr),
                                    "Suspicious 0dp dimension: in a vertical `LinearLayout` with weights, "
                                            + "the width should not be 0dp");
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean hasNonZeroWeight(@NonNull Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isZeroDp(@NonNull String value) {
        return value.equals("0dp") || value.equals("0dip");
    }
}