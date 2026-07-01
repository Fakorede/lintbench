package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            InefficientWeightDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "NestedWeights",
            "Nested layout weights",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with " +
            "non-zero weights is nested inside another `LinearLayout` with non-zero weights, " +
            "then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasNonZeroWeight(element)) {
            return;
        }

        if (hasWeightedLinearLayoutAncestor(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Nested weights are bad for performance");
        }
    }

    private static boolean hasNonZeroWeight(@NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return false;
        }
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        try {
            return Float.parseFloat(weight) != 0.0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasWeightedLinearLayoutAncestor(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (TAG_LINEAR_LAYOUT.equals(parentElement.getTagName())
                    && hasNonZeroWeight(parentElement)) {
                return true;
            }
            parent = parentElement.getParentNode();
        }
        return false;
    }
}