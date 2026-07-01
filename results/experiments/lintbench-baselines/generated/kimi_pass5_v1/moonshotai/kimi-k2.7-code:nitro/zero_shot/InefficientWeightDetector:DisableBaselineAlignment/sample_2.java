package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends ResourceXmlDetector {

    public static final Issue DISABLE_BASELINE_ALIGNMENT = Issue.create(
            "DisableBaselineAlignment",
            "Missing baselineAligned attribute",
            "When a LinearLayout is used to distribute the space proportionally between nested layouts, " +
                    "the baseline alignment property should be turned off to make the layout computation faster. " +
                    "Set android:baselineAligned=\"false\" on the LinearLayout.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Baseline alignment only applies to horizontal LinearLayouts.
        if (SdkConstants.VALUE_VERTICAL.equals(element.getAttribute(SdkConstants.ATTR_ORIENTATION))) {
            return;
        }

        // Already disabled; nothing to do.
        if (SdkConstants.VALUE_FALSE.equals(element.getAttribute(SdkConstants.ATTR_BASELINE_ALIGNED))) {
            return;
        }

        boolean hasWeightedLayoutChild = false;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (hasLayoutWeight(childElement) && isLayout(childElement.getTagName())) {
                hasWeightedLayoutChild = true;
                break;
            }
        }

        if (hasWeightedLayoutChild) {
            context.report(
                    DISABLE_BASELINE_ALIGNMENT,
                    context.getLocation(element),
                    "Set android:baselineAligned=\"false\" to improve layout performance"
            );
        }
    }

    private static boolean hasLayoutWeight(Element element) {
        String weight = element.getAttribute(SdkConstants.ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Double.parseDouble(weight) != 0.0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static boolean isLayout(String tagName) {
        return tagName != null && tagName.endsWith("Layout");
    }
}