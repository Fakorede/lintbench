package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
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
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        if (SdkConstants.VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED)) {
            return;
        }

        boolean hasWeightedLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                if (child.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    if (isLayout(child)) {
                        hasWeightedLayout = true;
                        break;
                    }
                }
            }
        }

        if (hasWeightedLayout) {
            LintFix fix = fix()
                    .set(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED, SdkConstants.VALUE_FALSE)
                    .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this `LinearLayout`",
                    fix
            );
        }
    }

    private static boolean isLayout(Element element) {
        String tag = element.getTagName();
        return tag.endsWith("Layout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("ListView")
                || tag.equals("GridView")
                || tag.equals("ViewPager")
                || tag.equals("RecyclerView")
                || tag.equals("NestedScrollView")
                || tag.equals("ViewAnimator")
                || tag.equals("ViewSwitcher")
                || tag.equals("CardView");
    }
}