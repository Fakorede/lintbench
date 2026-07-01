package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a "
                            + "LinearLayout with non-zero weights is nested inside another "
                            + "LinearLayout with non-zero weights, then the number of "
                            + "measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        if (hasNonZeroWeightChild(element)) {
            Node parent = element.getParentNode();
            while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (LINEAR_LAYOUT.equals(parentElement.getTagName())
                        && hasNonZeroWeightChild(parentElement)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Nested layout weights are bad for performance");
                    return;
                }
                parent = parent.getParentNode();
            }
        }
    }

    private static boolean hasNonZeroWeightChild(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (hasNonZeroWeight((Element) child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonZeroWeight(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT).trim();
        if (value.isEmpty()) {
            return false;
        }
        if (value.startsWith("@")) {
            return true;
        }
        try {
            return Float.parseFloat(value) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}