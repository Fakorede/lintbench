package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String VALUE_FALSE = "false";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a horizontal `LinearLayout` uses `layout_weight` to distribute space "
                            + "among nested layouts, the baseline-alignment calculation is "
                            + "unnecessary and makes measure/layout slower. Set "
                            + "`android:baselineAligned=\"false\"` on the parent `LinearLayout` "
                            + "to improve performance.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!LINEAR_LAYOUT.equals(element.getTagName())) {
            return;
        }

        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        boolean hasWeightedNestedLayout = false;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (hasWeight(childElement) && isLayout(childElement)) {
                    hasWeightedNestedLayout = true;
                    break;
                }
            }
            child = child.getNextSibling();
        }

        if (hasWeightedNestedLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set `android:baselineAligned=\"false\"` to improve performance when using "
                            + "weights in a horizontal LinearLayout with nested layouts");
        }
    }

    private static boolean hasWeight(@NonNull Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            return Float.parseFloat(weight) != 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLayout(@NonNull Element element) {
        String tag = element.getTagName();
        return tag.endsWith("Layout");
    }
}