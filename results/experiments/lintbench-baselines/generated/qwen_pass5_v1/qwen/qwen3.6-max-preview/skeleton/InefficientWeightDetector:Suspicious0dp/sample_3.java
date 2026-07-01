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
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) child, isVertical);
            }
        }
    }

    private void checkChild(@NonNull XmlContext context, @NonNull Element child, boolean isVertical) {
        String weightStr = child.getAttributeNS(ANDROID_URI, "layout_weight");
        if (weightStr == null || weightStr.isEmpty()) {
            return;
        }

        try {
            float weight = Float.parseFloat(weightStr);
            if (weight <= 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }

        if (isVertical) {
            String width = child.getAttributeNS(ANDROID_URI, "layout_width");
            if ("0dp".equals(width) || "0dip".equals(width)) {
                context.report(
                        ISSUE,
                        context.getLocation(child.getAttributeNodeNS(ANDROID_URI, "layout_width")),
                        "Suspicious size: 0dp width in a vertical LinearLayout");
            }
        } else {
            String height = child.getAttributeNS(ANDROID_URI, "layout_height");
            if ("0dp".equals(height) || "0dip".equals(height)) {
                context.report(
                        ISSUE,
                        context.getLocation(child.getAttributeNodeNS(ANDROID_URI, "layout_height")),
                        "Suspicious size: 0dp height in a horizontal LinearLayout");
            }
        }
    }
}