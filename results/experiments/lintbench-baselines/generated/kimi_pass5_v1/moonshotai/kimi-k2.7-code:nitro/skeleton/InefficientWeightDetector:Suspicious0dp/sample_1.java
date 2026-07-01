package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful trick "
                            + "to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children.\n\n"
                            + "However, if you use 0dp for the opposite dimension, the view will be "
                            + "invisible. This can happen if you change the orientation of a layout "
                            + "without also flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean horizontal = orientation.isEmpty() || "horizontal".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            String weight = child.getAttributeNS(ANDROID_URI, "layout_weight");
            if (weight.isEmpty()) {
                continue;
            }

            float weightValue;
            try {
                weightValue = Float.parseFloat(weight);
            } catch (NumberFormatException e) {
                weightValue = 1f;
            }
            if (weightValue <= 0f) {
                continue;
            }

            if (horizontal) {
                String height = child.getAttributeNS(ANDROID_URI, "layout_height");
                if (isZeroDp(height)) {
                    context.report(
                            ISSUE,
                            child,
                            context.getValueLocation(child, "layout_height"),
                            "Suspicious 0dp height in a horizontal LinearLayout with layout_weight; "
                                    + "the view will be invisible");
                }
            } else {
                String width = child.getAttributeNS(ANDROID_URI, "layout_width");
                if (isZeroDp(width)) {
                    context.report(
                            ISSUE,
                            child,
                            context.getValueLocation(child, "layout_width"),
                            "Suspicious 0dp width in a vertical LinearLayout with layout_weight; "
                                    + "the view will be invisible");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        value = value.trim();
        String number;
        if (value.endsWith("dip")) {
            number = value.substring(0, value.length() - 3);
        } else if (value.endsWith("dp")) {
            number = value.substring(0, value.length() - 2);
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