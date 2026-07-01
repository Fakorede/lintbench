package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Inefficient layout weight",
                    "When a LinearLayout is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned "
                            + "off by setting `android:baselineAligned=\"false\"` to make the "
                            + "layout computation faster.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            return;
        }

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (weight == null || weight.isEmpty()) {
                continue;
            }

            try {
                if (Float.parseFloat(weight) > 0) {
                    context.report(
                            ISSUE,
                            element,
                            context.getElementLocation(element),
                            "Set `android:baselineAligned=\"false\"` on this LinearLayout to improve "
                                    + "layout performance when using layout weights.");
                    return;
                }
            } catch (NumberFormatException e) {
                // Ignore invalid weight values.
            }
        }
    }
}