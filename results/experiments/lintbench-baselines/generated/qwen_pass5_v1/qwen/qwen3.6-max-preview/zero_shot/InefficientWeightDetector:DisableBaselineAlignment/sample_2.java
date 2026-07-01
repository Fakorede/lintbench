package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "DisableBaselineAlignment",
        "Missing `baselineAligned` attribute",
        "When a `LinearLayout` is used to distribute the space proportionally between " +
        "nested layouts, the baseline alignment property should be turned off to " +
        "make the layout computation faster.",
        Category.PERFORMANCE,
        Severity.WARNING,
        new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, "orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(SdkConstants.ANDROID_URI, "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(SdkConstants.ANDROID_URI, "layout_weight");
                if (weight != null && !weight.isEmpty()) {
                    context.report(ISSUE, element, context.getLocation(element),
                        "Set android:baselineAligned=\"false\" on this element for better performance");
                    return;
                }
            }
        }
    }
}