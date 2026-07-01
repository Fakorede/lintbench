package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        if (SdkConstants.VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED);
        if (SdkConstants.VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                if (child.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    if (isLayout(child)) {
                        context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Set `android:baselineAligned=\"false\"` on this `LinearLayout` to speed up layout builds"
                        );
                        return;
                    }
                }
            }
        }
    }

    private boolean isLayout(Element element) {
        String tag = element.getTagName();
        return tag.endsWith("Layout")
                || tag.equals(SdkConstants.SCROLL_VIEW)
                || tag.equals("NestedScrollView")
                || tag.equals(SdkConstants.GRID_VIEW)
                || tag.equals(SdkConstants.LIST_VIEW)
                || tag.equals("ViewPager")
                || tag.contains(".");
    }
}