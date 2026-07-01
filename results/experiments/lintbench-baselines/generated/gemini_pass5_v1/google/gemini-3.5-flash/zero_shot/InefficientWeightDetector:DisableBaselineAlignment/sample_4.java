package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between "
                    + "nested layouts, the baseline alignment property should be turned off to "
                    + "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.LAYOUT_RESOURCE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Baseline alignment only applies to horizontal orientation.
        // If orientation is vertical, baseline alignment has no effect.
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        if (SdkConstants.VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        // If the baselineAligned attribute is already configured, do not warn.
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED)) {
            return;
        }

        boolean hasLayoutChildWithWeight = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    if (isLayout(childElement)) {
                        hasLayoutChildWithWeight = true;
                        break;
                    }
                }
            }
        }

        if (hasLayoutChildWithWeight) {
            LintFix fix = fix()
                    .set(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED, SdkConstants.VALUE_FALSE)
                    .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance",
                    fix
            );
        }
    }

    private static boolean isLayout(@NonNull Element element) {
        String tagName = element.getTagName();
        return tagName.endsWith("Layout")
                || tagName.equals("ViewGroup")
                || tagName.equals("fragment")
                || tagName.equals("ScrollView")
                || tagName.equals("HorizontalScrollView")
                || tagName.equals("NestedScrollView")
                || tagName.equals("ViewAnimator")
                || tagName.equals("AdapterViewFlipper")
                || tagName.equals("StackView")
                || tagName.equals("ViewFlipper")
                || tagName.equals("ViewSwitcher");
    }
}