package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_ZERO_DP;

import com.android.annotations.NonNull;
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
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        // Default orientation is horizontal
        boolean isHorizontal = !VALUE_VERTICAL.equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;

            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            if (isHorizontal) {
                // In a horizontal LinearLayout, 0dp width is the efficient trick.
                // 0dp height is suspicious (the view would be invisible).
                if (VALUE_ZERO_DP.equals(height)) {
                    Attr heightAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                    String message =
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`. Did you mean to use `0dp` for "
                                    + "`layout_width` instead?";
                    if (heightAttr != null) {
                        context.report(ISSUE, childElement, context.getLocation(heightAttr), message);
                    } else {
                        context.report(ISSUE, childElement, context.getLocation(childElement), message);
                    }
                }
            } else {
                // In a vertical LinearLayout, 0dp height is the efficient trick.
                // 0dp width is suspicious (the view would be invisible).
                if (VALUE_ZERO_DP.equals(width)) {
                    Attr widthAttr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                    String message =
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`. Did you mean to use `0dp` for "
                                    + "`layout_height` instead?";
                    if (widthAttr != null) {
                        context.report(ISSUE, childElement, context.getLocation(widthAttr), message);
                    } else {
                        context.report(ISSUE, childElement, context.getLocation(childElement), message);
                    }
                }
            }
        }
    }
}