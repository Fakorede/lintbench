package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue NESTED_WEIGHTS = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_LAYOUT_WEIGHT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (isZero(value)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        while (parentNode instanceof Element) {
            Element parentElement = (Element) parentNode;
            String tagName = parentElement.getTagName();
            if (isLinearLayout(tagName)) {
                Attr parentWeight = parentElement.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (parentWeight != null && !isZero(parentWeight.getValue())) {
                    context.report(
                            NESTED_WEIGHTS,
                            attribute,
                            context.getLocation(attribute),
                            "Nested weights are bad for performance"
                    );
                    break;
                }
            }
            parentNode = parentElement.getParentNode();
        }
    }

    private static boolean isZero(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            return Double.parseDouble(value) == 0.0;
        } catch (NumberFormatException e) {
            return "0".equals(value) || "0.0".equals(value);
        }
    }

    private static boolean isLinearLayout(String tagName) {
        return tagName.equals(LINEAR_LAYOUT)
                || tagName.equals("android.widget.LinearLayout")
                || tagName.equals("androidx.appcompat.widget.LinearLayoutCompat")
                || tagName.endsWith(".LinearLayout");
    }
}