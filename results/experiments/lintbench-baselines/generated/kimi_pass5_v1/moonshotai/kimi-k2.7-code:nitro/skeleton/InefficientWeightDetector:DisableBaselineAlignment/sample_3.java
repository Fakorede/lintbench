package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ORIENTATION = "orientation";
    private static final String BASELINE_ALIGNED = "baselineAligned";
    private static final String LAYOUT_WEIGHT = "layout_weight";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a horizontal LinearLayout uses layout weights to distribute space between nested layouts, baseline alignment is unnecessary and should be disabled so that the layout pass is faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        String orientation = element.getAttributeNS(ANDROID_URI, ORIENTATION);
        if ("vertical".equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            return;
        }

        if (hasWeightedLayoutChild(element)) {
            LintFix fix =
                    LintFix.create()
                            .set(ANDROID_URI, BASELINE_ALIGNED, "false")
                            .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `baselineAligned` to false when distributing space with weights in a horizontal LinearLayout",
                    fix);
        }
    }

    private static boolean hasWeightedLayoutChild(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (isLayout(childElement) && hasWeight(childElement)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLayout(Element element) {
        String tag = element.getTagName();
        return tag != null && tag.endsWith("Layout");
    }

    private static boolean hasWeight(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}