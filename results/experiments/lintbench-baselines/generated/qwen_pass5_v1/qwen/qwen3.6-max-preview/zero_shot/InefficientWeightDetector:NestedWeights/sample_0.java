package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a LinearLayout with " +
            "non-zero weights is nested inside another LinearLayout with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE, 3, Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr weightAttr = element.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        float weight;
        try {
            weight = Float.parseFloat(weightAttr.getValue());
        } catch (NumberFormatException e) {
            return;
        }

        if (weight <= 0.0f) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent instanceof Element) {
                Element parentElement = (Element) parent;
                String tag = parentElement.getTagName();
                if (tag.equals(SdkConstants.LINEAR_LAYOUT) || tag.endsWith(SdkConstants.DOT_LINEAR_LAYOUT)) {
                    Attr parentWeightAttr = parentElement.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (parentWeightAttr != null) {
                        try {
                            float parentWeight = Float.parseFloat(parentWeightAttr.getValue());
                            if (parentWeight > 0.0f) {
                                context.report(ISSUE, context.getLocation(weightAttr),
                                        "Nested weights are bad for performance");
                                return;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore malformed weight in parent
                        }
                    }
                }
            }
            parent = parent.getParentNode();
        }
    }
}