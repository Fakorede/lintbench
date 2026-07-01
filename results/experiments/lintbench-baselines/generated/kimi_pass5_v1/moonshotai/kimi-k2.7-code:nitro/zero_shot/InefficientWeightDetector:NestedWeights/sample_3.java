package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

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
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<Issue> getIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasWeightedChild(element)) {
            Element ancestor = getParentLinearLayout(element);
            while (ancestor != null) {
                if (hasWeightedChild(ancestor)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Nested layout weights are inefficient");
                    break;
                }
                ancestor = getParentLinearLayout(ancestor);
            }
        }
    }

    private static boolean hasWeightedChild(@NonNull Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        if (Float.parseFloat(weight) != 0f) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // Could be a reference; assume it's non-zero.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static Element getParentLinearLayout(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if (LINEAR_LAYOUT.equals(parentElement.getTagName())) {
                    return parentElement;
                }
            }
            parent = parent.getParentNode();
        }
        return null;
    }
}