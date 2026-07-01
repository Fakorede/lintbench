package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

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
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr weightAttr = getWeightAttribute(element);
        if (weightAttr != null) {
            String weight = weightAttr.getValue();
            if (weight != null && !weight.isEmpty()) {
                try {
                    float w = Float.parseFloat(weight.trim());
                    if (w > 0.0f) {
                        Node parent = element.getParentNode();
                        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                            Element parentElement = (Element) parent;
                            if (SdkConstants.LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                                Attr parentWeightAttr = getWeightAttribute(parentElement);
                                if (parentWeightAttr != null) {
                                    String parentWeight = parentWeightAttr.getValue();
                                    if (parentWeight != null && !parentWeight.isEmpty()) {
                                        float pw = Float.parseFloat(parentWeight.trim());
                                        if (pw > 0.0f) {
                                            context.report(ISSUE, context.getLocation(weightAttr),
                                                    "Nested weights are bad for performance");
                                            return;
                                        }
                                    }
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

    private static Attr getWeightAttribute(Element element) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (attr == null) {
            attr = element.getAttributeNode(SdkConstants.ANDROID_PREFIX + SdkConstants.ATTR_LAYOUT_WEIGHT);
        }
        return attr;
    }
}