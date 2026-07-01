package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr weightAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        String weightValue = weightAttr.getValue();
        try {
            float weight = Float.parseFloat(weightValue);
            if (weight <= 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            // Value might be a resource reference; assume it represents a valid weight
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        if (!SdkConstants.LINEAR_LAYOUT.equals(parent.getTagName())) {
            return;
        }

        String orientation = parent.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        boolean isHorizontal = !SdkConstants.VALUE_VERTICAL.equals(orientation);

        if (isHorizontal) {
            Attr heightAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
            if (heightAttr != null && isZeroDp(heightAttr.getValue())) {
                context.report(ISSUE, context.getLocation(heightAttr),
                        "Suspicious size: this will make the view invisible, should be used with `layout_width`");
            }
        } else {
            Attr widthAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
            if (widthAttr != null && isZeroDp(widthAttr.getValue())) {
                context.report(ISSUE, context.getLocation(widthAttr),
                        "Suspicious size: this will make the view invisible, should be used with `layout_height`");
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}