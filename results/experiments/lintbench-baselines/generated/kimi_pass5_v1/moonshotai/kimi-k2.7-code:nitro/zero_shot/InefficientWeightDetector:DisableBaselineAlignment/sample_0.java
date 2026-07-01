package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ORIENTATION_VERTICAL = "vertical";

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between nested "
                    + "layouts, the baseline alignment property should be turned off to make the "
                    + "layout computation faster. Set `baselineAligned=\"false\"` on the "
                    + "`LinearLayout`.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ORIENTATION);
        if (ORIENTATION_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned =
                element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight.isEmpty()) {
                continue;
            }

            float weightValue;
            try {
                weightValue = Float.parseFloat(weight);
            } catch (NumberFormatException e) {
                continue;
            }

            if (weightValue > 0f && isLayout(childElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Set `baselineAligned=\"false\"` on this LinearLayout to improve layout performance");
                return;
            }
        }
    }

    private static boolean isLayout(@NotNull Element element) {
        String tag = element.getTagName();
        return tag.endsWith("Layout");
    }
}