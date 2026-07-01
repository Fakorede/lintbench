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
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing baselineAligned attribute",
                    "When a LinearLayout is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off "
                            + "to make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, "layout_weight");
                if (!weight.isEmpty()) {
                    String tag = childElement.getTagName();
                    if (tag.endsWith("Layout")) {
                        hasWeightedChildLayout = true;
                        break;
                    }
                }
            }
        }

        if (hasWeightedChildLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set baselineAligned=\"false\" on this LinearLayout for faster layout "
                            + "computation when children use layout_weight");
        }
    }
}