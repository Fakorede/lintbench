package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
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
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children.\n\n"
                            + "However, if you use 0dp for the opposite dimension, the view will be invisible. "
                            + "This can happen if you change the orientation of a layout without also flipping "
                            + "the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String width = child.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width");
                String height = child.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_height");

                if (isVertical) {
                    if (isZero(width)) {
                        Attr attribute = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_width");
                        context.report(
                                ISSUE,
                                child,
                                context.getLocation(attribute != null ? attribute : child),
                                "Suspicious size: this will make the view invisible in a vertical layout");
                    }
                } else {
                    if (isZero(height)) {
                        Attr attribute = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_height");
                        context.report(
                                ISSUE,
                                child,
                                context.getLocation(attribute != null ? attribute : child),
                                "Suspicious size: this will make the view invisible in a horizontal layout");
                    }
                }
            }
        }
    }

    private boolean isZero(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("^0+(?:\\.0+)?\\s*(?:dp|dip|px|sp|in|mm|pt)?$");
    }
}