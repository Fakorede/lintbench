package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            2,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
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

        String baselineAligned = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED);
        if (SdkConstants.VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasLayoutChildWithWeight = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tagName = child.getTagName();
                if (isLayout(tagName) && child.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    hasLayoutChildWithWeight = true;
                    break;
                }
            }
        }

        if (hasLayoutChildWithWeight) {
            LintFix fix = fix()
                    .set(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED, SdkConstants.VALUE_FALSE)
                    .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance",
                    fix
            );
        }
    }

    private static boolean isLayout(String tagName) {
        return tagName.endsWith("Layout")
                || tagName.equals("ScrollView")
                || tagName.equals("NestedScrollView")
                || tagName.equals("ListView")
                || tagName.equals("GridView")
                || tagName.equals("ViewPager")
                || tagName.equals("RecyclerView");
    }
}