package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        return java.util.Arrays.asList("LinearLayout", "androidx.appcompat.widget.LinearLayoutCompat");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, "orientation");
        if ("vertical".equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, "baselineAligned");
        if ("false".equals(baselineAligned)) {
            return;
        }

        boolean hasNestedLayoutWithWeight = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                String tagName = child.getTagName();
                if (isLayout(tagName) && child.hasAttributeNS(ANDROID_URI, "layout_weight")) {
                    hasNestedLayoutWithWeight = true;
                    break;
                }
            }
        }

        if (hasNestedLayoutWithWeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }

    private boolean isLayout(String tagName) {
        int dot = tagName.lastIndexOf('.');
        String simpleName = dot != -1 ? tagName.substring(dot + 1) : tagName;
        return simpleName.endsWith("Layout") 
                || "ViewGroup".equals(simpleName) 
                || "ViewStub".equals(simpleName);
    }
}