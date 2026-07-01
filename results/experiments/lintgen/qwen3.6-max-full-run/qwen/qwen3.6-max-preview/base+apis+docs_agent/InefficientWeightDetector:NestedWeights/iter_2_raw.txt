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

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";

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
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String weightStr = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weightStr == null || weightStr.isEmpty()) {
            weightStr = element.getAttribute("android:" + ATTR_LAYOUT_WEIGHT);
        }

        if (weightStr != null && !weightStr.isEmpty()) {
            try {
                float weight = Float.parseFloat(weightStr.trim());
                if (weight > 0.0f) {
                    Node parent = element.getParentNode();
                    while (parent != null) {
                        if (parent.getNodeType() == Node.ELEMENT_NODE) {
                            Element parentEl = (Element) parent;
                            if (TAG_LINEAR_LAYOUT.equals(parentEl.getTagName())) {
                                String parentWeightStr = parentEl.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                                if (parentWeightStr == null || parentWeightStr.isEmpty()) {
                                    parentWeightStr = parentEl.getAttribute("android:" + ATTR_LAYOUT_WEIGHT);
                                }
                                if (parentWeightStr != null && !parentWeightStr.isEmpty()) {
                                    float parentWeight = Float.parseFloat(parentWeightStr.trim());
                                    if (parentWeight > 0.0f) {
                                        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                                        if (attr == null) {
                                            attr = element.getAttributeNode("android:" + ATTR_LAYOUT_WEIGHT);
                                        }
                                        context.report(ISSUE, context.getLocation(attr != null ? attr : element),
                                                "Nested weights are bad for performance");
                                    }
                                }
                                break;
                            }
                        }
                        parent = parent.getParentNode();
                    }
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric weights (e.g., resource references)
            }
        }
    }
}