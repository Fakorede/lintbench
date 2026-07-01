package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

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

    public static final Issue NESTED_WEIGHTS =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` "
                            + "with non-zero weights is nested inside another `LinearLayout` with "
                            + "non-zero weights, then the number of measurements increase "
                            + "exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has a non-zero layout_weight attribute
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return;
        }

        // Check that the weight is non-zero
        try {
            float weightValue = Float.parseFloat(weight);
            if (weightValue == 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            // If we can't parse it, assume it's non-zero and continue checking
        }

        // Walk up the DOM tree to find if there's an ancestor LinearLayout with non-zero weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getTagName())
                    || LINEAR_LAYOUT.equals(parentElement.getLocalName())) {
                // Found an ancestor LinearLayout; check if it has any children with non-zero weights
                if (hasChildrenWithWeights(parentElement)) {
                    context.report(
                            NESTED_WEIGHTS,
                            element,
                            context.getLocation(element),
                            "Nested weights are bad for performance. "
                                    + "Consider using a flat layout instead.");
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    /**
     * Returns true if the given LinearLayout element has at least one direct child
     * with a non-zero layout_weight attribute.
     */
    private static boolean hasChildrenWithWeights(@NonNull Element linearLayout) {
        Node child = linearLayout.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        float weightValue = Float.parseFloat(weight);
                        if (weightValue != 0f) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // Assume non-zero
                        return true;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }
}