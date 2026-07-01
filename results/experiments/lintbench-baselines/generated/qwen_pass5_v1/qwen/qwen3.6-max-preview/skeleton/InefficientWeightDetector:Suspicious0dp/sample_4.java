package com.android.tools.lint.checks;

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
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttribute("android:orientation");
        boolean isVertical = "vertical".equals(orientation);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weightStr = childElement.getAttribute("android:layout_weight");
                if (weightStr.isEmpty()) {
                    continue;
                }

                float weight;
                try {
                    weight = Float.parseFloat(weightStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                if (weight <= 0.0f) {
                    continue;
                }

                String width = childElement.getAttribute("android:layout_width");
                String height = childElement.getAttribute("android:layout_height");

                if (isVertical) {
                    if (isZeroDp(width)) {
                        context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement.getAttributeNode("android:layout_width")),
                                "Suspicious size: `layout_width` is `0dp` in a vertical `LinearLayout`; did you mean `layout_height`?");
                    }
                } else {
                    if (isZeroDp(height)) {
                        context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement.getAttributeNode("android:layout_height")),
                                "Suspicious size: `layout_height` is `0dp` in a horizontal `LinearLayout`; did you mean `layout_width`?");
                    }
                }
            }
        }
    }

    private static boolean isZeroDp(String value) {
        return "0dp".equals(value) || "0dip".equals(value);
    }
}