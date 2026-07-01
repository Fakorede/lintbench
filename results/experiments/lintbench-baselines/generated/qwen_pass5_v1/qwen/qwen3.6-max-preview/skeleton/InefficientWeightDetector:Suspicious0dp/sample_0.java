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
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful trick to ensure that only the weights (and not the intrinsic sizes) are used when sizing the children.\n\n" +
                    "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
                    "This can happen if you change the orientation of a layout without also flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        String suspiciousAttr = isVertical ? "layout_width" : "layout_height";

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String value = child.getAttributeNS(ANDROID_URI, suspiciousAttr);
                if (isZeroDp(value)) {
                    context.report(
                            ISSUE,
                            child,
                            context.getLocation(child.getAttributeNodeNS(ANDROID_URI, suspiciousAttr)),
                            "Suspicious 0dp dimension on the " + (isVertical ? "horizontal" : "vertical") + " axis in a " + (isVertical ? "vertical" : "horizontal") + " LinearLayout");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        if (value == null) return false;
        return value.equals("0dp") || value.equals("0dip") || value.equals("0px") || value.equals("0");
    }
}