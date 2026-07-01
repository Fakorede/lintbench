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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkChild(context, (Element) child, isVertical);
            }
        }
    }

    private void checkChild(@NonNull XmlContext context, @NonNull Element child, boolean isVertical) {
        String weight = child.getAttributeNS(ANDROID_URI, "layout_weight");
        if (weight == null || weight.isEmpty()) {
            return;
        }

        String crossAttrName = isVertical ? "layout_width" : "layout_height";
        String crossValue = child.getAttributeNS(ANDROID_URI, crossAttrName);

        if ("0dp".equals(crossValue) || "0dip".equals(crossValue)) {
            String message = isVertical
                    ? "Suspicious size: width is 0dp in a vertical LinearLayout. Did you mean height to be 0dp?"
                    : "Suspicious size: height is 0dp in a horizontal LinearLayout. Did you mean width to be 0dp?";
            context.report(ISSUE, context.getLocation(child, crossAttrName), message);
        }
    }
}