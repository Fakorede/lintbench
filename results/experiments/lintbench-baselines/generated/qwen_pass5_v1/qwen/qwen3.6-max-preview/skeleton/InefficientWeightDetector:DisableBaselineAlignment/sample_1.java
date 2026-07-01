package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a LinearLayout is used to distribute the space proportionally between nested layouts, " +
                    "the baseline alignment property should be turned off to make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String ATTR_WEIGHT_SUM = "weightSum";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            return;
        }

        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        boolean hasWeight = element.hasAttributeNS(ANDROID_URI, ATTR_WEIGHT_SUM);
        if (!hasWeight) {
            for (Element child : LintUtils.getChildren(element)) {
                if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    hasWeight = true;
                    break;
                }
            }
        }

        if (hasWeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set android:baselineAligned=\"false\" on this element for better performance");
        }
    }
}