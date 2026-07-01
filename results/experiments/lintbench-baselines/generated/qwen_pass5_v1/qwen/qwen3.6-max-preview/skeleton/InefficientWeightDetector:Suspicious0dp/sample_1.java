package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful " +
                    "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
                    "when sizing the children. However, if you use 0dp for the opposite dimension, " +
                    "the view will be invisible. This can happen if you change the orientation of a " +
                    "layout without also flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String weightStr = element.getAttributeNS(ANDROID_URI, "layout_weight");
        if (weightStr.isEmpty()) {
            return;
        }

        float weight;
        try {
            weight = Float.parseFloat(weightStr);
        } catch (NumberFormatException e) {
            return;
        }

        if (weight <= 0.0f) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String tag = parent.getTagName();
        if (!"LinearLayout".equals(tag) && !tag.endsWith(":LinearLayout")) {
            return;
        }

        String orientation = parent.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        String wrongAttr = isVertical ? "layout_width" : "layout_height";
        String dimen = element.getAttributeNS(ANDROID_URI, wrongAttr);

        if ("0dp".equals(dimen) || "0dip".equals(dimen)) {
            Node attrNode = element.getAttributeNodeNS(ANDROID_URI, wrongAttr);
            context.report(ISSUE, context.getLocation(attrNode),
                    "Suspicious 0dp dimension: in a " + (isVertical ? "vertical" : "horizontal") +
                    " LinearLayout, the " + wrongAttr + " should not be 0dp when layout_weight is used");
        }
    }
}