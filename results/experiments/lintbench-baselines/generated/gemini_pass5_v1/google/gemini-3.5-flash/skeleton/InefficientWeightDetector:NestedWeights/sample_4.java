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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with non-zero weights is nested inside another `LinearLayout` with non-zero weights, then the number of measurements increase exponentially.",
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
        String weight = element.getAttributeNS(ANDROID_URI, "layout_weight");
        if (isNonZeroWeight(weight)) {
            Node parentNode = element.getParentNode();
            if (parentNode instanceof Element) {
                Element parent = (Element) parentNode;
                if (parent.getTagName().equals("LinearLayout")) {
                    if (hasChildWithWeight(element)) {
                        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, "layout_weight");
                        context.report(
                                ISSUE,
                                weightAttr != null ? weightAttr : element,
                                context.getLocation(weightAttr != null ? weightAttr : element),
                                "Nested weights are bad for performance");
                    }
                }
            }
        }
    }

    private boolean isNonZeroWeight(String weight) {
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(weight.trim());
            return value != 0.0;
        } catch (NumberFormatException e) {
            return !weight.equals("0") && !weight.equals("0.0");
        }
    }

    private boolean hasChildWithWeight(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode instanceof Element) {
                Element child = (Element) childNode;
                String weight = child.getAttributeNS(ANDROID_URI, "layout_weight");
                if (isNonZeroWeight(weight)) {
                    return true;
                }
            }
        }
        return false;
    }
}