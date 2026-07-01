package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a LinearLayout is used to distribute the space proportionally between nested layouts, "
                            + "the baseline alignment property should be turned off to make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String baselineAligned = element.getAttribute("android:baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        String orientation = element.getAttribute("android:orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        boolean hasWeight = element.hasAttribute("android:weightSum");
        if (!hasWeight) {
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (childElement.hasAttribute("android:layout_weight")) {
                        hasWeight = true;
                        break;
                    }
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