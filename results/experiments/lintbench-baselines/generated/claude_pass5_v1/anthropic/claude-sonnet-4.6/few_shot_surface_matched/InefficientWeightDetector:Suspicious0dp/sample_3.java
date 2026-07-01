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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_ZERO_DP;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using `0dp` as the width in a horizontal `LinearLayout` with weights is a "
                            + "useful trick to ensure that only the weights (and not the intrinsic "
                            + "sizes) are used when sizing the children.\n\n"
                            + "However, if you use `0dp` for the opposite dimension, the view will "
                            + "be invisible. This can happen if you change the orientation of a "
                            + "layout without also flipping the `0dp` dimension in all the "
                            + "children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);

        // Default orientation is horizontal
        boolean isHorizontal =
                orientation == null
                        || orientation.isEmpty()
                        || VALUE_HORIZONTAL.equals(orientation);
        boolean isVertical = VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            // Only check children that have a layout_weight set
            String weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            String width = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String height = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            boolean widthIsZero = VALUE_ZERO_DP.equals(width);
            boolean heightIsZero = VALUE_ZERO_DP.equals(height);

            if (isHorizontal && heightIsZero) {
                // 0dp height in a horizontal LinearLayout with weight => invisible
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)),
                        "Suspicious size: this will make the view invisible, should be "
                                + "used with `layout_weight`. Did you mean to use `0dp` for "
                                + "`layout_width` instead?");
            } else if (isVertical && widthIsZero) {
                // 0dp width in a vertical LinearLayout with weight => invisible
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)),
                        "Suspicious size: this will make the view invisible, should be "
                                + "used with `layout_weight`. Did you mean to use `0dp` for "
                                + "`layout_height` instead?");
            }
        }
    }
}