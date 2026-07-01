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

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has a non-zero layout_weight attribute
        String weight = element.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return;
        }

        // Check if the weight is non-zero
        try {
            float weightValue = Float.parseFloat(weight);
            if (weightValue == 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            // If we can't parse it, assume it's non-zero
        }

        // Now check if any ancestor is a LinearLayout that also has children with weights
        // i.e., check if this element is nested inside another LinearLayout that uses weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getLocalName())
                    || LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                // Found an ancestor LinearLayout - check if it has children with non-zero weights
                if (hasChildrenWithWeights(parentElement)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Nested weights are bad for performance. Consider using a "
                                    + "flat layout instead");
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasChildrenWithWeights(Element linearLayout) {
        org.w3c.dom.NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        float weightValue = Float.parseFloat(weight);
                        if (weightValue != 0f) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // If we can't parse it, assume it's non-zero
                        return true;
                    }
                }
            }
        }
        return false;
    }
}