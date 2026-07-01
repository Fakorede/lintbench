package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.LayoutDetector;
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
                    "When a `LinearLayout` is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off to "
                            + "make the layout computation faster.",
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
        String namespace = "http://schemas.android.com/apk/res/android";

        // If orientation is vertical, baseline alignment is not used/applicable.
        String orientation = element.getAttributeNS(namespace, "orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        // If baselineAligned is already set to false, it's optimized.
        String baselineAligned = element.getAttributeNS(namespace, "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        // Look for nested layouts that have a layout_weight attribute.
        NodeList children = element.getChildNodes();
        boolean hasWeightedLayoutChild = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                String tagName = child.getTagName();
                if (isLayout(tagName) && child.hasAttributeNS(namespace, "layout_weight")) {
                    hasWeightedLayoutChild = true;
                    break;
                }
            }
        }

        if (hasWeightedLayoutChild) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this `LinearLayout` to speed up layout rendering");
        }
    }

    private boolean isLayout(String tagName) {
        return tagName.endsWith("Layout") || tagName.equals("ViewGroup") || tagName.contains("Layout");
    }
}