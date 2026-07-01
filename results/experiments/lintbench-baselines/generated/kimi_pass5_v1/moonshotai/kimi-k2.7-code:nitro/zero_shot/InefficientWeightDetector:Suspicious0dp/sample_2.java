package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                    + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                    + "used when sizing the children.\n"
                    + "However, if you use 0dp for the opposite dimension, the view will be "
                    + "invisible. This can happen if you change the orientation of a layout "
                    + "without also flipping the 0dp dimension in all the children.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasNonZeroWeight(childElement)) {
                    if (isVertical) {
                        if (isZeroDimension(childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH))) {
                            reportSuspicious0dp(context, childElement, ATTR_LAYOUT_WIDTH, isVertical);
                        }
                    } else {
                        if (isZeroDimension(childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT))) {
                            reportSuspicious0dp(context, childElement, ATTR_LAYOUT_HEIGHT, isVertical);
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
            return Float.parseFloat(weight) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isZeroDimension(@Nullable String value) {
        return value != null
                && (value.equals("0dp") || value.equals("0dip") || value.equals("0"));
    }

    private static void reportSuspicious0dp(@NonNull XmlContext context,
            @NonNull Element element, @NonNull String attribute, boolean isVertical) {
        String used = ATTR_LAYOUT_WIDTH.equals(attribute) ? "width" : "height";
        String expected = ATTR_LAYOUT_WIDTH.equals(attribute) ? "height" : "width";
        String orientation = isVertical ? "vertical" : "horizontal";
        String message = String.format(
                "Suspicious 0dp dimension: in a %s LinearLayout with weights, "
                        + "0dp should be used on the %s, not the %s",
                orientation, expected, used);
        context.report(ISSUE, element, context.getAttributeLocation(element, attribute), message);
    }
}