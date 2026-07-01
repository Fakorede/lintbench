package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

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

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using `0dp` as the width in a horizontal `LinearLayout` with weights is a "
                            + "useful trick to ensure that only the weights (and not the intrinsic "
                            + "sizes) are used when sizing the children.\n"
                            + "\n"
                            + "However, if you use `0dp` for the opposite dimension, the view will "
                            + "be invisible. This can happen if you change the orientation of a "
                            + "layout without also flipping the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public InefficientWeightDetector() {
    }

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

            if (isVertical) {
                // Vertical LinearLayout: weight applies along vertical axis (height).
                // Using 0dp for width (the non-weight dimension) would make it invisible.
                if (is0dp(width) && !is0dp(height)) {
                    context.report(
                            ISSUE,
                            childElement,
                            context.getNameLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`. Did you mean to use "
                                    + "`match_parent` or `wrap_content` instead of `0dp`?");
                }
            } else {
                // Horizontal LinearLayout (default): weight applies along horizontal axis (width).
                // Using 0dp for height (the non-weight dimension) would make it invisible.
                if (is0dp(height) && !is0dp(width)) {
                    context.report(
                            ISSUE,
                            childElement,
                            context.getNameLocation(childElement),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`. Did you mean to use "
                                    + "`match_parent` or `wrap_content` instead of `0dp`?");
                }
            }
        }
    }

    private static boolean is0dp(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return false;
        }
        if (VALUE_MATCH_PARENT.equals(dimension)
                || VALUE_FILL_PARENT.equals(dimension)
                || VALUE_WRAP_CONTENT.equals(dimension)) {
            return false;
        }
        return dimension.startsWith("0");
    }
}