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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WEIGHT = "layoutWeight";
    private static final String VALUE_FALSE = "false";
    private static final String VALUE_VERTICAL = "vertical";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` uses `layout_weight` to distribute space among nested "
                            + "layouts, baseline alignment forces an expensive baseline calculation "
                            + "that is usually unnecessary. Setting "
                            + "`android:baselineAligned=\"false\"` on the `LinearLayout` improves "
                            + "layout performance.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_LINEAR_LAYOUT.equals(element.getNodeName())) {
            return;
        }

        Attr baselineAligned = element.getAttributeNodeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (baselineAligned != null && VALUE_FALSE.equals(baselineAligned.getValue())) {
            return;
        }

        Attr orientation = element.getAttributeNodeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (orientation != null && VALUE_VERTICAL.equals(orientation.getValue())) {
            return;
        }

        boolean hasWeightedLayoutChild = false;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isLayout(childElement) && hasNonZeroWeight(childElement)) {
                    hasWeightedLayoutChild = true;
                    break;
                }
            }
            child = child.getNextSibling();
        }

        if (!hasWeightedLayoutChild) {
            return;
        }

        LintFix fix;
        if (baselineAligned == null) {
            fix = LintFix.create().set(ANDROID_URI, ATTR_BASELINE_ALIGNED, VALUE_FALSE).build();
        } else {
            fix =
                    LintFix.create()
                            .replace()
                            .text(baselineAligned.getValue())
                            .with(VALUE_FALSE)
                            .build();
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Set `android:baselineAligned=\"false\"` on this LinearLayout for better performance",
                fix);
    }

    private static boolean isLayout(@NonNull Element element) {
        String tag = element.getTagName();
        return tag != null && tag.endsWith("Layout");
    }

    private static boolean hasNonZeroWeight(@NonNull Element element) {
        Attr weight = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight == null) {
            return false;
        }
        String value = weight.getValue();
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return Double.parseDouble(value) != 0.0;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}