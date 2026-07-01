package com.android.tools.lint.checks;

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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

public class InefficientWeightDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "Suspicious0dp",
            "Suspicious 0dp dimension",
            "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful " +
            "trick to ensure that only the weights (and not the intrinsic sizes) are used " +
            "when sizing the children.\n\n" +
            "However, if you use 0dp for the opposite dimension, the view will be invisible. " +
            "This can happen if you change the orientation of a layout without also flipping " +
            "the `0dp` dimension in all the children.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isHorizontal = !VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            Attr weightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weightAttr == null) {
                continue;
            }
            String weightValue = weightAttr.getValue();
            if (weightValue == null || weightValue.isEmpty()) {
                continue;
            }
            try {
                float weight = Float.parseFloat(weightValue);
                if (weight <= 0.0f) {
                    continue;
                }
            } catch (NumberFormatException e) {
                // Assume valid resource reference
            }

            if (isHorizontal) {
                Attr heightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (heightAttr != null && isZeroDp(heightAttr.getValue())) {
                    context.report(ISSUE, context.getLocation(heightAttr),
                            "Suspicious size: this will make the view invisible, should be used with layout_width");
                }
            } else {
                Attr widthAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (widthAttr != null && isZeroDp(widthAttr.getValue())) {
                    context.report(ISSUE, context.getLocation(widthAttr),
                            "Suspicious size: this will make the view invisible, should be used with layout_height");
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}