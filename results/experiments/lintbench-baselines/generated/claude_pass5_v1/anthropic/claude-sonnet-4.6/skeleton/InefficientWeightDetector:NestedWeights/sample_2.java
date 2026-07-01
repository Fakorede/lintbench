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

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has any children with non-zero layout_weight
        if (!hasChildWithWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has non-zero weights
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (LINEAR_LAYOUT.equals(parentElement.getLocalName())
                    || LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                // Check if this ancestor LinearLayout has children with weights
                if (hasChildWithWeight(parentElement)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Nested weights are bad for performance. Consider using a flat layout "
                                    + "instead.");
                    return;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasChildWithWeight(Element linearLayout) {
        Node child = linearLayout.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty() && !weight.equals("0")) {
                    return true;
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }
}