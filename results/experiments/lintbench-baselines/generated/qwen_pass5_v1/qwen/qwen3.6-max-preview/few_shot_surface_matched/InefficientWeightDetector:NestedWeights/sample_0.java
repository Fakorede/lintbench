package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a LinearLayout with "
                    + "non-zero weights is nested inside another LinearLayout with non-zero weights, "
                    + "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (getWeight(element) > 0.0f) {
            Element parent = getParentLinearLayoutWithWeight(element);
            if (parent != null) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Nested weights are bad for performance");
            }
        }
    }

    private static float getWeight(@NonNull Element element) {
        String weight = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight != null && !weight.isEmpty()) {
            try {
                return Float.parseFloat(weight);
            } catch (NumberFormatException ignored) {
                // Ignore malformed weight values
            }
        }
        return 0.0f;
    }

    @Nullable
    private static Element getParentLinearLayoutWithWeight(@NonNull Element element) {
        Node current = element.getParentNode();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) current;
                if (SdkConstants.LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                    if (getWeight(parentElement) > 0.0f) {
                        return parentElement;
                    }
                }
            }
            current = current.getParentNode();
        }
        return null;
    }
}