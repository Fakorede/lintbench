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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String LINEAR_LAYOUT = "LinearLayout";

    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    private static final String VALUE_VERTICAL = "vertical";
    private static final String VALUE_0_DP = "0dp";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) "
                            + "are used when sizing the children. However, if you use 0dp for the "
                            + "opposite dimension, the view will be invisible. This can happen if "
                            + "you change the orientation of a layout without also flipping the "
                            + "0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element child = (Element) childNode;
            if (!hasLayoutWeight(child)) {
                continue;
            }

            if (isVertical) {
                if (isZeroDp(child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH))) {
                    reportSuspicious0dp(context, child, ATTR_LAYOUT_WIDTH);
                }
            } else {
                if (isZeroDp(child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT))) {
                    reportSuspicious0dp(context, child, ATTR_LAYOUT_HEIGHT);
                }
            }
        }
    }

    private static boolean hasLayoutWeight(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        return !weight.isEmpty();
    }

    private static boolean isZeroDp(String value) {
        return value != null && (VALUE_0_DP.equals(value) || "0dip".equals(value));
    }

    private static void reportSuspicious0dp(
            XmlContext context, Element element, String attributeName) {
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName);
        context.report(
                ISSUE,
                element,
                attribute != null ? context.getLocation(attribute) : context.getLocation(element),
                "Suspicious 0dp dimension: using 0dp for "
                        + attributeName
                        + " in this LinearLayout makes the view invisible");
    }
}