package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
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
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is " +
                    "a useful trick to ensure that only the weights (and not the intrinsic sizes) " +
                    "are used when sizing the children.\n\n" +
                    "However, if you use 0dp for the opposite dimension, the view will be " +
                    "invisible. This can happen if you change the orientation of a layout " +
                    "without also flipping the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "LinearLayout",
                "android.support.v7.widget.LinearLayoutCompat",
                "androidx.appcompat.widget.LinearLayoutCompat"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                if (child.hasAttributeNS(ANDROID_URI, "layout_weight")) {
                    String targetAttr = isVertical ? "layout_width" : "layout_height";
                    String value = child.getAttributeNS(ANDROID_URI, targetAttr);
                    if (isZero(value)) {
                        Attr attributeNode = child.getAttributeNodeNS(ANDROID_URI, targetAttr);
                        Location location = attributeNode != null ? context.getLocation(attributeNode) : context.getLocation(child);
                        String orientationName = isVertical ? "vertical" : "horizontal";
                        context.report(
                                ISSUE,
                                child,
                                location,
                                "Suspicious size: this will make the view invisible in a " + orientationName + " layout");
                    }
                }
            }
        }
    }

    private boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.equals("0")) {
            return true;
        }
        if (value.startsWith("0")) {
            String unit = value.substring(1).trim();
            return unit.equals("dp") || unit.equals("dip") || unit.equals("px") ||
                   unit.equals("sp") || unit.equals("in") || unit.equals("mm") || unit.equals("pt");
        }
        return false;
    }
}