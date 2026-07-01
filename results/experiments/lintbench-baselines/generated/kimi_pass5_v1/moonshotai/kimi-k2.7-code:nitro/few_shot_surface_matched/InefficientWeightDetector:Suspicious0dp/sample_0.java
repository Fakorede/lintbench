package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful"
                            + " trick to ensure that only the weights are used when sizing the"
                            + " children. However, using 0dp for the opposite dimension will make"
                            + " the view invisible. This can happen if the orientation of a"
                            + " layout is changed without also flipping the 0dp dimension in all"
                            + " children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ORIENTATION_VERTICAL = "vertical";
    private static final String ZERO_DP = "0dp";
    private static final String ZERO_DIP = "0dip";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = ORIENTATION_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (!hasLayoutWeight(childElement)) {
                continue;
            }

            if (isVertical) {
                Attr width = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (width != null && isZeroDimension(width.getValue())) {
                    context.report(
                            ISSUE,
                            width,
                            context.getLocation(width),
                            "Suspicious 0dp dimension: the `layout_width` of a child in a vertical"
                                    + " `LinearLayout` is 0dp, which will make the view invisible");
                }
            } else {
                Attr height = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (height != null && isZeroDimension(height.getValue())) {
                    context.report(
                            ISSUE,
                            height,
                            context.getLocation(height),
                            "Suspicious 0dp dimension: the `layout_height` of a child in a"
                                    + " horizontal `LinearLayout` is 0dp, which will make the view"
                                    + " invisible");
                }
            }
        }
    }

    private static boolean hasLayoutWeight(Element element) {
        Attr weight = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        return weight != null && !weight.getValue().isEmpty();
    }

    private static boolean isZeroDimension(String value) {
        return value != null
                && (ZERO_DP.equalsIgnoreCase(value) || ZERO_DIP.equalsIgnoreCase(value));
    }
}