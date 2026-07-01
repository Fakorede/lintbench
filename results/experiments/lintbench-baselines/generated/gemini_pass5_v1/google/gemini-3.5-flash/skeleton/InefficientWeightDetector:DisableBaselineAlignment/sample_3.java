package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String VALUE_FALSE = "false";
    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String LINEAR_LAYOUT_FW = "android.widget.LinearLayout";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off "
                            + "to make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(LINEAR_LAYOUT, LINEAR_LAYOUT_FW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        boolean hasLayoutChildWithWeight = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    String tag = child.getTagName();
                    int lastDot = tag.lastIndexOf('.');
                    String localName = lastDot != -1 ? tag.substring(lastDot + 1) : tag;
                    if (localName.endsWith("Layout")
                            || "View".equals(localName)
                            || "fragment".equals(localName)
                            || "WebView".equals(localName)) {
                        hasLayoutChildWithWeight = true;
                        break;
                    }
                }
            }
        }

        if (hasLayoutChildWithWeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance");
        }
    }
}