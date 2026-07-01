package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";

    private static final String EXPLANATION =
            "Layout weights require a widget to be measured twice. When a LinearLayout "
                    + "with non-zero weights is nested inside another LinearLayout with "
                    + "non-zero weights, the number of measurements increases exponentially. "
                    + "This can hurt scrolling and overall UI performance. Consider using "
                    + "ConstraintLayout, RelativeLayout, or other flat layout structures.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    EXPLANATION,
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // The inner LinearLayout must itself use layout_weight inside its parent.
        if (!hasNonZeroWeight(element)) {
            return;
        }

        // It must be nested directly inside another LinearLayout.
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)
                || !TAG_LINEAR_LAYOUT.equals(((Element) parent).getTagName())) {
            return;
        }

        // The inner LinearLayout must also have children that use layout_weight.
        if (hasWeightedChild(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Nested layout weights may cause exponential measurement overhead");
        }
    }

    private static boolean hasWeightedChild(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && hasNonZeroWeight((Element) child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonZeroWeight(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight.isEmpty()) {
            weight = element.getAttribute(ATTR_LAYOUT_WEIGHT);
        }
        return isNonZero(weight);
    }

    private static boolean isNonZero(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        // Resource references cannot be resolved here, so assume they are non-zero.
        if (trimmed.startsWith("@")) {
            return true;
        }

        try {
            return Float.parseFloat(trimmed) != 0f;
        } catch (NumberFormatException e) {
            // Unrecognized value; treat as potentially non-zero to be safe.
            return true;
        }
    }
}