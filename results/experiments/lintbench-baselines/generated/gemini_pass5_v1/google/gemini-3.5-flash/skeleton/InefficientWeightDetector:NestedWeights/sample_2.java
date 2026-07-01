package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a LinearLayout with "
                            + "non-zero weights is nested inside another LinearLayout with non-zero "
                            + "weights, then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("LinearLayout", "android.widget.LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasWeight(element)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element ancestor = (Element) parent;
                String tagName = ancestor.getTagName();
                if ((tagName.equals("LinearLayout") || tagName.equals("android.widget.LinearLayout"))
                        && hasWeight(ancestor)) {
                    Attr attribute = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "layout_weight");
                    Location location = attribute != null ? context.getLocation(attribute) : context.getLocation(element);
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "Nested layout weights can heavily hurt performance");
                    break;
                }
            }
            parent = parent.getParentNode();
        }
    }

    private boolean hasWeight(Element element) {
        String weight = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_weight");
        if (weight == null || weight.isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(weight.trim());
            return value > 0.0;
        } catch (NumberFormatException e) {
            return !weight.equals("0") && !weight.equals("0.0");
        }
    }
}