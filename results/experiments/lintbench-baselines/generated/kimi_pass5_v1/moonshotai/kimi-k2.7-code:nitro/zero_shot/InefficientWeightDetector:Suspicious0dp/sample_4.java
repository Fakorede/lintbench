package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_0DP;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
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

    private static final Implementation IMPLEMENTATION = new Implementation(
            InefficientWeightDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
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
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean vertical = VALUE_VERTICAL.equals(orientation);

        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            Attr weightNode = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weightNode == null) {
                continue;
            }

            float weight;
            try {
                weight = Float.parseFloat(weightNode.getValue());
            } catch (NumberFormatException e) {
                continue;
            }

            if (weight <= 0) {
                continue;
            }

            if (vertical) {
                Attr widthNode = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (widthNode != null && VALUE_0DP.equals(widthNode.getValue())) {
                    context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(widthNode),
                            "Suspicious 0dp dimension: `layout_width` equals `0dp` in a vertical "
                                    + "`LinearLayout` with weights; this will make the view "
                                    + "invisible");
                }
            } else {
                Attr heightNode = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                if (heightNode != null && VALUE_0DP.equals(heightNode.getValue())) {
                    context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(heightNode),
                            "Suspicious 0dp dimension: `layout_height` equals `0dp` in a "
                                    + "horizontal `LinearLayout` with weights; this will make the "
                                    + "view invisible");
                }
            }
        }
    }
}