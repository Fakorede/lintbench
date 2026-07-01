package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
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
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are "
                            + "used when sizing the children.\n"
                            + "\n"
                            + "However, if you use 0dp for the opposite dimension, the view will be "
                            + "invisible. This can happen if you change the orientation of a layout "
                            + "without also flipping the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (isVertical) {
                    Attr widthAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    if (widthAttr != null && isZero(widthAttr.getValue())) {
                        context.report(
                                ISSUE,
                                widthAttr,
                                context.getLocation(widthAttr),
                                "Suspicious size: this will make the view invisible in a vertical layout");
                    }
                } else {
                    Attr heightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    if (heightAttr != null && isZero(heightAttr.getValue())) {
                        context.report(
                                ISSUE,
                                heightAttr,
                                context.getLocation(heightAttr),
                                "Suspicious size: this will make the view invisible in a horizontal layout");
                    }
                }
            }
        }
    }

    private static boolean isZero(@NonNull String value) {
        return value.matches("0\\s*(px|dp|dip|sp|in|mm)?");
    }
}