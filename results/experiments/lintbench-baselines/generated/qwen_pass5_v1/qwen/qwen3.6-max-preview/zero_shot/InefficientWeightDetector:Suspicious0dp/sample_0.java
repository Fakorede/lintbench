package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

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
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        for (Element child : LintUtils.getChildren(element)) {
            String weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight.isEmpty()) {
                continue;
            }

            try {
                if (Float.parseFloat(weight) <= 0f) {
                    continue;
                }
            } catch (NumberFormatException e) {
                continue;
            }

            String width = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String height = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            boolean zeroWidth = isZeroDp(width);
            boolean zeroHeight = isZeroDp(height);

            if (isVertical) {
                if (zeroWidth) {
                    report(context, child, ATTR_LAYOUT_WIDTH, "vertical");
                }
            } else {
                if (zeroHeight) {
                    report(context, child, ATTR_LAYOUT_HEIGHT, "horizontal");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }

    private static void report(XmlContext context, Element child, String attrName, String orientation) {
        Attr attr = child.getAttributeNodeNS(ANDROID_URI, attrName);
        if (attr != null) {
            String message = String.format(
                    "Suspicious size: this will make the view invisible, " +
                    "probably intended for the other dimension (orientation is %1$s)", orientation);
            context.report(ISSUE, context.getLocation(attr), message);
        }
    }
}