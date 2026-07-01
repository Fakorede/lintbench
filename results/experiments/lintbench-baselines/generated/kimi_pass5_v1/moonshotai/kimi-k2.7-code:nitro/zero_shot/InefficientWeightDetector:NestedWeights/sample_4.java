package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;

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

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with non-zero weights is nested inside another `LinearLayout` with non-zero weights, then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasWeights(element)) {
            Node parent = element.getParentNode();
            while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (TAG_LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                    if (hasWeights(parentElement)) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Nested layout weights are bad for performance");
                        return;
                    }
                }
                parent = parent.getParentNode();
            }
        }
    }

    private static boolean hasWeights(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (!weight.isEmpty()) {
            try {
                if (Float.parseFloat(weight) != 0.0f) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (!weight.isEmpty()) {
                    try {
                        if (Float.parseFloat(weight) != 0.0f) {
                            return true;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }

        return false;
    }
}