package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Inefficient weight",
                    "When a LinearLayout is used to distribute space proportionally between nested "
                            + "layouts, the baseline alignment property should be turned off to make "
                            + "the layout computation faster.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        Element child = Detector.getFirstChildElement(element);
        while (child != null) {
            if (LINEAR_LAYOUT.equals(child.getTagName())) {
                String weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    String baseline = child.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
                    if (!VALUE_FALSE.equals(baseline)) {
                        context.report(
                                ISSUE,
                                child,
                                context.getLocation(child),
                                "Set `baselineAligned` to `false` when a LinearLayout's children "
                                        + "use weights, to avoid expensive baseline alignment");
                    }
                }
            }
            child = Detector.getNextSiblingElement(child);
        }
    }
}