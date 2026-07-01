package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                    + "non-zero weights is nested inside another `LinearLayout` with non-zero "
                    + "weights, then the number of measurements increase exponentially.",
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
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (isZero(value)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!isLinearLayout(element.getParentNode())) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            Node grandparent = parent.getParentNode();
            if (grandparent instanceof Element && isLinearLayout(grandparent)) {
                Element parentElement = (Element) parent;
                if (parentElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    String parentWeight = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (!isZero(parentWeight)) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                "Nested weights are bad for performance"
                        );
                        break;
                    }
                }
            }
            parent = grandparent;
        }
    }

    private boolean isZero(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            float val = Float.parseFloat(value.trim());
            return val == 0.0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isLinearLayout(Node node) {
        if (!(node instanceof Element)) {
            return false;
        }
        String tagName = ((Element) node).getTagName();
        return tagName.equals(SdkConstants.NODE_LINEAR_LAYOUT)
                || tagName.equals("android.widget.LinearLayout")
                || tagName.endsWith(".LinearLayout")
                || tagName.equals("RadioGroup")
                || tagName.equals("android.widget.RadioGroup");
    }
}