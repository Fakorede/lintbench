package com.android.tools.lint.checks;

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

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String VALUE_HORIZONTAL = "horizontal";
    private static final String VALUE_0DP = "0dp";

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal LinearLayout with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic "
                            + "sizes) are used when sizing children. However, if you use 0dp for "
                            + "the opposite dimension, the view will be invisible. This can "
                            + "happen if you change the orientation of a layout without also "
                            + "flipping the 0dp dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            String weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight.isEmpty()) {
                continue;
            }

            String attrName = isVertical ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
            Attr attr = child.getAttributeNodeNS(ANDROID_URI, attrName);
            if (attr == null) {
                continue;
            }

            if (VALUE_0DP.equals(attr.getValue())) {
                String dimension = isVertical ? "width" : "height";
                String orientationName = isVertical ? "vertical" : "horizontal";
                String message = String.format(
                        "Suspicious 0dp %s in a %s LinearLayout with layout_weight; "
                                + "the view will be invisible",
                        dimension, orientationName);
                context.report(ISSUE, attr, context.getValueLocation(attr), message);
            }
        }
    }
}