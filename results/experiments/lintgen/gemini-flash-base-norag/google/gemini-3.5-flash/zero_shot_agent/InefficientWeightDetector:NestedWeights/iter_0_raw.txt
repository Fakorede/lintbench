package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a "
                    + "LinearLayout with non-zero weights is nested inside another "
                    + "LinearLayout with non-zero weights, then the number of "
                    + "measurements increase exponentially.",
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
        return Collections.singletonList(SdkConstants.ATTR_LAYOUT_WEIGHT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (isZero(value)) {
            return;
        }

        Node parent = attribute.getOwnerElement().getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) parent;
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                String parentValue = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                if (!isZero(parentValue)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Nested weights are bad for performance"
                    );
                    break;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean isZero(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            return Double.parseDouble(value.trim()) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}