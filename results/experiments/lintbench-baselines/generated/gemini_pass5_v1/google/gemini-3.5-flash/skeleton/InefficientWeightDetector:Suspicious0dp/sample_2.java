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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children. However, if you use 0dp for the opposite dimension, "
                            + "the view will be invisible. This can happen if you change the orientation of "
                            + "a layout without also flipping the 0dp dimension in all the children.",
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
        String namespace = "http://schemas.android.com/apk/res/android";
        String orientation = element.getAttributeNS(namespace, "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.hasAttributeNS(namespace, "layout_weight")) {
                    if (isVertical) {
                        if (child.hasAttributeNS(namespace, "layout_width")) {
                            String width = child.getAttributeNS(namespace, "layout_width");
                            if (isZero(width)) {
                                Attr attribute = child.getAttributeNodeNS(namespace, "layout_width");
                                Location location = attribute != null ? context.getLocation(attribute) : context.getLocation(child);
                                context.report(
                                        ISSUE,
                                        child,
                                        location,
                                        "Suspicious size: this will make the view invisible; did you mean to set layout_height to 0dp instead of layout_width?");
                            }
                        }
                    } else {
                        if (child.hasAttributeNS(namespace, "layout_height")) {
                            String height = child.getAttributeNS(namespace, "layout_height");
                            if (isZero(height)) {
                                Attr attribute = child.getAttributeNodeNS(namespace, "layout_height");
                                Location location = attribute != null ? context.getLocation(attribute) : context.getLocation(child);
                                context.report(
                                        ISSUE,
                                        child,
                                        location,
                                        "Suspicious size: this will make the view invisible; did you mean to set layout_width to 0dp instead of layout_height?");
                            }
                        }
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
                || trimmed.equals("0");
    }
}