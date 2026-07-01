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
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                            + "non-zero weights is nested inside another `LinearLayout` with non-zero weights, "
                            + "then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has a non-zero layout_weight attribute
        if (!hasNonZeroWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has a non-zero weight child
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                String tagName = parentElement.getTagName();
                if ("LinearLayout".equals(tagName) || tagName.endsWith(".LinearLayout")) {
                    if (hasChildWithNonZeroWeight(parentElement)) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Nested weights are bad for performance");
                        return;
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasNonZeroWeight(Element element) {
        String weight = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "layout_weight");
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            float weightValue = Float.parseFloat(weight);
            return weightValue != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasChildWithNonZeroWeight(Element linearLayout) {
        org.w3c.dom.NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasNonZeroWeight(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }
}