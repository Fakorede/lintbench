package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

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
        return Arrays.asList("LinearLayout", "android.widget.LinearLayout");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr weightAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightAttr == null) {
            return;
        }

        String weightStr = weightAttr.getValue();
        if (weightStr == null || weightStr.isEmpty()) {
            return;
        }

        try {
            float weight = Float.parseFloat(weightStr.trim());
            if (weight > 0.0f) {
                Node parent = element.getParentNode();
                while (parent != null && parent.getNodeType() != Node.ELEMENT_NODE) {
                    parent = parent.getParentNode();
                }

                if (parent != null) {
                    Element parentEl = (Element) parent;
                    String parentTag = parentEl.getTagName();
                    if (parentTag.endsWith("LinearLayout")) {
                        Attr parentWeightAttr = parentEl.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        if (parentWeightAttr != null) {
                            String parentWeightStr = parentWeightAttr.getValue();
                            if (parentWeightStr != null && !parentWeightStr.isEmpty()) {
                                float parentWeight = Float.parseFloat(parentWeightStr.trim());
                                if (parentWeight > 0.0f) {
                                    context.report(ISSUE, context.getLocation(weightAttr),
                                            "Nested weights are bad for performance");
                                }
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException ignored) {
            // Ignore non-numeric weights (e.g., resource references)
        }
    }
}