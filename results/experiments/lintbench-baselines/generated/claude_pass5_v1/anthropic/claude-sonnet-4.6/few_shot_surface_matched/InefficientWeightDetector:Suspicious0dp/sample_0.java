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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a "
                            + "useful trick to ensure that only the weights (and not the intrinsic "
                            + "sizes) are used when sizing the children.\n\n"
                            + "However, if you use 0dp for the opposite dimension, the view will "
                            + "be invisible. This can happen if you change the orientation of a "
                            + "layout without also flipping the `0dp` dimension in all the "
                            + "children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        boolean isHorizontal;
        if (VALUE_VERTICAL.equals(orientation)) {
            isHorizontal = false;
        } else {
            // Default orientation is horizontal
            isHorizontal = true;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    if (isHorizontal) {
                        // In a horizontal LinearLayout, 0dp width is good (uses weight only)
                        // but 0dp height is bad (makes view invisible)
                        String height = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
                        if (VALUE_ZERO_DP.equals(height)) {
                            context.report(
                                    ISSUE,
                                    childElement,
                                    context.getLocation(
                                            childElement.getAttributeNodeNS(
                                                    ANDROID_URI, ATTR_LAYOUT_HEIGHT)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_width` instead");
                        }
                    } else {
                        // In a vertical LinearLayout, 0dp height is good (uses weight only)
                        // but 0dp width is bad (makes view invisible)
                        String width = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                        if (VALUE_ZERO_DP.equals(width)) {
                            context.report(
                                    ISSUE,
                                    childElement,
                                    context.getLocation(
                                            childElement.getAttributeNodeNS(
                                                    ANDROID_URI, ATTR_LAYOUT_WIDTH)),
                                    "Suspicious size: this will make the view invisible, should be "
                                            + "used with `layout_height` instead");
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }
}