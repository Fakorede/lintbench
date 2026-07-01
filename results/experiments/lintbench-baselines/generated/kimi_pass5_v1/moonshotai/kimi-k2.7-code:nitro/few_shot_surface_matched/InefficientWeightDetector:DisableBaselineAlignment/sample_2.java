package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Inefficient layout weight",
                    "When a `LinearLayout` is used to distribute the space proportionally between"
                            + " nested layouts, the baseline alignment property should be turned"
                            + " off to make the layout computation faster.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String baselineAligned = element.getAttribute(ATTR_BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            return;
        }

        for (Node child = element.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String weight = childElement.getAttribute(ATTR_LAYOUT_WEIGHT);
            if (!weight.isEmpty()) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Set `android:baselineAligned=\"false\"` to improve performance in this"
                                + " LinearLayout when weights are used");
                return;
            }
        }
    }
}