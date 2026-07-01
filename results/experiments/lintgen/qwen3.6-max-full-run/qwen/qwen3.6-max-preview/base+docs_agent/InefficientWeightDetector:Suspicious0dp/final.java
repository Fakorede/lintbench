package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "Suspicious0dp",
        "Suspicious 0dp dimension",
        "Using 0dp as the width in a horizontal LinearLayout with weights is a useful " +
        "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
        "when sizing the children. However, if you use 0dp for the opposite dimension, " +
        "the view will be invisible. This can happen if you change the orientation of a " +
        "layout without also flipping the 0dp dimension in all the children.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isVertical = SdkConstants.VALUE_VERTICAL.equals(orientation);

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) child, isVertical);
            }
            child = child.getNextSibling();
        }
    }

    private void checkChild(XmlContext context, Element child, boolean isVertical) {
        String weight = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return;
        }

        String width = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        String height = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (isVertical) {
            if (isZeroDp(width)) {
                context.report(ISSUE, child,
                    context.getLocation(child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)),
                    "Suspicious size: this will make the view invisible, should be used with `layout_height` instead of `layout_width`");
            }
        } else {
            if (isZeroDp(height)) {
                context.report(ISSUE, child,
                    context.getLocation(child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)),
                    "Suspicious size: this will make the view invisible, should be used with `layout_width` instead of `layout_height`");
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}