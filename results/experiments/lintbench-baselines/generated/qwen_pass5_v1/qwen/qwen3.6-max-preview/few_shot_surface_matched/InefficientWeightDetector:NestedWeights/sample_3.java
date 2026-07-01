package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested weights are bad for performance",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with "
                            + "non-zero weights is nested inside another LinearLayout with non-zero weights, "
                            + "then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String weightStr = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightStr == null || weightStr.isEmpty()) {
            return;
        }

        try {
            float weight = Float.parseFloat(weightStr);
            if (weight == 0f) {
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                    String parentWeightStr = parentElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                    if (parentWeightStr != null && !parentWeightStr.isEmpty()) {
                        try {
                            float parentWeight = Float.parseFloat(parentWeightStr);
                            if (parentWeight > 0f) {
                                context.report(ISSUE, element, context.getLocation(element),
                                        "Nested weights are bad for performance");
                                return;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid format in parent
                        }
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }
}