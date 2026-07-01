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

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a "
                            + "`LinearLayout` with non-zero weights is nested inside another "
                            + "`LinearLayout` with non-zero weights, then the number of "
                            + "measurements increase exponentially.",
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
        // Check if this LinearLayout has any children with a layout_weight attribute
        if (!hasChildWithWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has children with weights
        if (isNestedInsideWeightedLinearLayout(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Nested weights are bad for performance. Consider using a flat layout "
                            + "instead.");
        }
    }

    /**
     * Returns true if the given LinearLayout has at least one direct child with a non-zero
     * layout_weight attribute.
     */
    private static boolean hasChildWithWeight(@NonNull Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty() && !weight.equals("0")) {
                    return true;
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    /**
     * Returns true if the given element is nested (directly or indirectly) inside a LinearLayout
     * that itself has children with non-zero layout_weight attributes.
     */
    private static boolean isNestedInsideWeightedLinearLayout(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getLocalName())
                    || LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                if (hasChildWithWeight(parentElement)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}