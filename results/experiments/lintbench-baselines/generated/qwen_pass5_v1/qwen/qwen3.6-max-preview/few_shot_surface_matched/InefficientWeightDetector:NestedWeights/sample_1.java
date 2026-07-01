package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String LINEAR_LAYOUT = "LinearLayout";

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a LinearLayout with " +
            "non-zero weights is nested inside another LinearLayout with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String weightStr = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightStr == null || weightStr.isEmpty()) {
            return;
        }
        try {
            float weight = Float.parseFloat(weightStr);
            if (weight > 0f) {
                Node parent = element.getParentNode();
                if (parent instanceof Element) {
                    Element parentElement = (Element) parent;
                    if (LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                        String parentWeightStr = parentElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        if (parentWeightStr != null && !parentWeightStr.isEmpty()) {
                            float parentWeight = Float.parseFloat(parentWeightStr);
                            if (parentWeight > 0f) {
                                context.report(ISSUE, element, context.getLocation(element),
                                        "Nested weights are bad for performance");
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }
}