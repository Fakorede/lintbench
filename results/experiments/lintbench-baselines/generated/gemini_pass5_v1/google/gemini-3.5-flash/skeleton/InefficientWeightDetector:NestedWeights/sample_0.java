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
import org.w3c.dom.NodeList;

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
        if (!hasWeights(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            String tagName = parentElement.getTagName();
            if ("LinearLayout".equals(tagName) || tagName.endsWith(".LinearLayout")) {
                if (hasWeights(parentElement)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Nested layout weights can be extremely expensive; when a "
                                    + "LinearLayout with a weight is nested within another LinearLayout with "
                                    + "a weight, layout must be measured twice");
                    break;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasWeights(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_weight")) {
                    String value = childElement.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_weight").trim();
                    if (!value.isEmpty() && !value.equals("0") && !value.equals("0.0") && !value.equals("0dp")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}