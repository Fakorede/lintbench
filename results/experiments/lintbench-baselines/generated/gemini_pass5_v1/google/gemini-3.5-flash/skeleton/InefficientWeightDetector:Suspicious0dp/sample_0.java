package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Attr;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children. However, if you use 0dp for the opposite dimension, "
                            + "the view will be invisible. This can happen if you change the orientation of "
                            + "a layout without also flipping the `0dp` dimension in all the children.",
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
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String width = child.getAttributeNS(ANDROID_URI, "layout_width");
                String height = child.getAttributeNS(ANDROID_URI, "layout_height");

                if (isVertical) {
                    if (isZero(width)) {
                        Attr attributeNode = child.getAttributeNodeNS(ANDROID_URI, "layout_width");
                        com.android.tools.lint.detector.api.Location location = attributeNode != null 
                                ? context.getLocation(attributeNode) 
                                : context.getLocation(child);
                        context.report(ISSUE, child, location, "Suspicious size: this will make the view invisible, should be used for `layout_height` in vertical layouts");
                    }
                } else {
                    if (isZero(height)) {
                        Attr attributeNode = child.getAttributeNodeNS(ANDROID_URI, "layout_height");
                        com.android.tools.lint.detector.api.Location location = attributeNode != null 
                                ? context.getLocation(attributeNode) 
                                : context.getLocation(child);
                        context.report(ISSUE, child, location, "Suspicious size: this will make the view invisible, should be used for `layout_width` in horizontal layouts");
                    }
                }
            }
        }
    }

    private boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equals("0dp")
                || trimmed.equals("0dip")
                || trimmed.equals("0px")
                || trimmed.equals("0sp")
                || trimmed.equals("0in")
                || trimmed.equals("0mm")
                || trimmed.equals("0pt")
                || trimmed.equals("0");
    }
}