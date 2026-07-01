package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children. However, if you use 0dp for the opposite dimension, "
                            + "the view will be invisible. This can happen if you change the orientation of a "
                            + "layout without also flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_XML_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttribute(SdkConstants.ATTR_ORIENTATION);
        boolean isHorizontal = !SdkConstants.VALUE_VERTICAL.equals(orientation);

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) child, isHorizontal);
            }
        }
    }

    private void checkChild(@NonNull XmlContext context, @NonNull Element child, boolean isHorizontal) {
        Attr weightAttr = child.getAttributeNode(SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        String weightValue = weightAttr.getValue();
        try {
            float weight = Float.parseFloat(weightValue);
            if (weight <= 0f) {
                return;
            }
        } catch (NumberFormatException ignored) {
            return;
        }

        String dimToCheck = isHorizontal ? SdkConstants.ATTR_LAYOUT_HEIGHT : SdkConstants.ATTR_LAYOUT_WIDTH;
        Attr dimAttr = child.getAttributeNode(SdkConstants.ANDROID_NS_NAME_PREFIX + dimToCheck);

        if (dimAttr != null && isZeroDp(dimAttr.getValue())) {
            String expectedDim = isHorizontal ? SdkConstants.ATTR_LAYOUT_WIDTH : SdkConstants.ATTR_LAYOUT_HEIGHT;
            context.report(
                    ISSUE,
                    dimAttr,
                    context.getLocation(dimAttr),
                    "Suspicious size: this will make the view invisible; "
                            + "should be used with `" + expectedDim + "` instead of `" + dimToCheck + "`");
        }
    }

    private static boolean isZeroDp(@NonNull String value) {
        return value.equals("0dp") || value.equals("0dip") || value.equals("0px") || value.equals("0");
    }
}