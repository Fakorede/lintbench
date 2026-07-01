package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful"
                            + " trick to ensure that only the weights (and not the intrinsic sizes)"
                            + " are used when sizing the children. However, if you use 0dp for the"
                            + " opposite dimension, the view will be invisible. This can happen if"
                            + " you change the orientation of a layout without also flipping the"
                            + " `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String VALUE_VERTICAL = "vertical";

    private static final Pattern ZERO_DIMENSION =
            Pattern.compile("0+(\\.0*)?(dp|dip|sp|px|pt|in|mm)?");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean vertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (!hasNonZeroWeight(weight)) {
                continue;
            }

            if (vertical) {
                Attr widthAttr =
                        childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (widthAttr != null && isZeroDimension(widthAttr.getValue())) {
                    context.report(
                            ISSUE,
                            widthAttr,
                            context.getLocation(widthAttr),
                            "Suspicious 0dp width in a vertical `LinearLayout` with"
                                    + " `layout_weight`; the view will be invisible");
                }
            } else {
                Attr heightAttr =
                        childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (heightAttr != null && isZeroDimension(heightAttr.getValue())) {
                    context.report(
                            ISSUE,
                            heightAttr,
                            context.getLocation(heightAttr),
                            "Suspicious 0dp height in a horizontal `LinearLayout` with"
                                    + " `layout_weight`; the view will be invisible");
                }
            }
        }
    }

    private static boolean hasNonZeroWeight(String weight) {
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isZeroDimension(String value) {
        return value != null && ZERO_DIMENSION.matcher(value).matches();
    }
}