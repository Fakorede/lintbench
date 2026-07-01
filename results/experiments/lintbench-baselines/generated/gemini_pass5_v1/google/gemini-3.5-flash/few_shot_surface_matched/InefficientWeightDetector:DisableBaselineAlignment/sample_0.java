package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

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
                    new Implementation(
                            InefficientWeightDetector.class,
                            Scope.LAYOUT_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS("http://schemas.android.com/apk/res/android", "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasWeightedLayout = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_weight")) {
                    String tagName = child.getTagName();
                    if (tagName.endsWith("Layout") || tagName.equals("ScrollView") || tagName.equals("ViewGroup")) {
                        hasWeightedLayout = true;
                        break;
                    }
                }
            }
        }

        if (hasWeightedLayout) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance"
            );
        }
    }
}