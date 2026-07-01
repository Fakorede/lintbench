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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a LinearLayout is used to distribute the space proportionally between nested layouts, the baseline alignment property should be turned off to make the layout computation faster.",
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
        String baselineAligned = element.getAttributeNS(ANDROID_URI, "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, "layout_weight");
                if (weight != null && !weight.isEmpty()) {
                    context.report(
                            ISSUE,
                            context.getLocation(element),
                            "Set `android:baselineAligned=\"false\"` on this layout for better performance");
                    return;
                }
            }
        }
    }
}