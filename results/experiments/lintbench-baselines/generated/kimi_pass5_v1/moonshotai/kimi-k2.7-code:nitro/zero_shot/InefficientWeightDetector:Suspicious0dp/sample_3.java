package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

public class InefficientWeightDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            InefficientWeightDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

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
            Severity.WARNING,
            IMPLEMENTATION);

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
        if (!LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        Attr orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean horizontal;
        if (orientationAttr == null) {
            horizontal = true;
        } else {
            String value = orientationAttr.getValue();
            horizontal = !VALUE_VERTICAL.equals(value);
        }

        Node childNode = element.getFirstChild();
        while (childNode != null) {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) childNode, horizontal);
            }
            childNode = childNode.getNextSibling();
        }
    }

    private static void checkChild(@NonNull XmlContext context, @NonNull Element child,
            boolean horizontal) {
        Attr weightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        String weightValue = weightAttr.getValue();
        if (weightValue == null || weightValue.isEmpty()) {
            return;
        }

        float weight;
        try {
            weight = Float.parseFloat(weightValue);
        } catch (NumberFormatException e) {
            return;
        }

        if (weight <= 0f) {
            return;
        }

        String suspiciousDimension;
        String correctDimension;
        String suspiciousAttribute;
        if (horizontal) {
            suspiciousDimension = "height";
            correctDimension = "width";
            suspiciousAttribute = ATTR_LAYOUT_HEIGHT;
        } else {
            suspiciousDimension = "width";
            correctDimension = "height";
            suspiciousAttribute = ATTR_LAYOUT_WIDTH;
        }

        Attr suspiciousAttr = child.getAttributeNodeNS(ANDROID_URI, suspiciousAttribute);
        if (suspiciousAttr == null) {
            return;
        }

        if (isZeroDp(suspiciousAttr.getValue())) {
            String message = String.format(
                    "Suspicious 0dp dimension: in a %s `LinearLayout`, `layout_%s` "
                            + "should be 0dp when using weights, not `layout_%s`",
                    horizontal ? "horizontal" : "vertical",
                    correctDimension,
                    suspiciousDimension);
            context.report(ISSUE, child, context.getLocation(suspiciousAttr), message);
        }
    }

    private static boolean isZeroDp(@Nullable String value) {
        if (value == null) {
            return false;
        }

        String number;
        String lower = value.toLowerCase(Locale.US);
        if (lower.endsWith("dp")) {
            number = value.substring(0, value.length() - 2);
        } else if (lower.endsWith("dip")) {
            number = value.substring(0, value.length() - 3);
        } else {
            return false;
        }

        try {
            return Float.parseFloat(number) == 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}