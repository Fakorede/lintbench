package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` "
                            + "with non-zero weights is nested inside another `LinearLayout` with "
                            + "non-zero weights, then the number of measurements increase "
                            + "exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this LinearLayout has any children with a layout_weight attribute
        if (!hasChildWithWeight(element)) {
            return;
        }

        // Check if any ancestor LinearLayout also has children with weights
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (LINEAR_LAYOUT.equals(parentElement.getTagName())
                        || LINEAR_LAYOUT.equals(getLocalName(parentElement))) {
                    if (hasChildWithWeight(parentElement)) {
                        context.report(
                                ISSUE,
                                element,
                                context.getElementLocation(element),
                                "Nested weights are bad for performance. "
                                        + "Consider using a flat layout instead.");
                        return;
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasChildWithWeight(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        double value = Double.parseDouble(weight);
                        if (value != 0) {
                            return true;
                        }
                    } catch (NumberFormatException ignore) {
                        // If we can't parse it, treat it as non-zero
                        return true;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private static String getLocalName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName;
        }
        String tagName = element.getTagName();
        int dotIndex = tagName.lastIndexOf('.');
        if (dotIndex != -1) {
            return tagName.substring(dotIndex + 1);
        }
        return tagName;
    }
}