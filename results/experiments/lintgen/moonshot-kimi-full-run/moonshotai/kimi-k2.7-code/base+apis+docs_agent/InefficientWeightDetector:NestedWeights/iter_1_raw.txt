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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a LinearLayout "
                    + "with non-zero weights is nested inside another LinearLayout with "
                    + "non-zero weights, the number of measurements increases exponentially.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasWeightedChild(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (SdkConstants.LINEAR_LAYOUT.equals(parentElement.getTagName())
                    && hasWeightedChild(parentElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getElementLocation(element),
                        "Nested layout weights will cause the views to be measured multiple times, "
                                + "which is expensive"
                );
                return;
            }
            parent = parent.getParentNode();
        }
    }

    private static boolean hasWeightedChild(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasNonZeroWeight(childElement)) {
                    return true;
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private static boolean hasNonZeroWeight(Element element) {
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(value) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}