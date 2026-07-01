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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this LinearLayout has any children with layout_weight
        // and those children are themselves layouts (ViewGroups)
        if (!hasChildWithWeight(element)) {
            return;
        }

        // Check if any weighted child is a nested layout
        if (!hasNestedLayoutChildWithWeight(element)) {
            return;
        }

        // Check if baselineAligned is already set to false
        Attr baselineAligned = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_BASELINE_ALIGNED
        );

        if (baselineAligned != null) {
            String value = baselineAligned.getValue();
            if (value.equals("false") || value.equals("@android:bool/false")) {
                return;
            }
            // It's explicitly set to true, no need to warn
            return;
        }

        // baselineAligned attribute is missing; report the issue
        LintFix fix = LintFix.create()
                .set(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED, "false")
                .build();

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Set `android:baselineAligned=\"false\"` on this element for better performance",
                fix
        );
    }

    private boolean hasChildWithWeight(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr weightAttr = childElement.getAttributeNodeNS(
                        SdkConstants.ANDROID_URI,
                        ATTR_LAYOUT_WEIGHT
                );
                if (weightAttr != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNestedLayoutChildWithWeight(Element linearLayout) {
        NodeList children = linearLayout.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                Attr weightAttr = childElement.getAttributeNodeNS(
                        SdkConstants.ANDROID_URI,
                        ATTR_LAYOUT_WEIGHT
                );
                if (weightAttr != null && isLayoutElement(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLayoutElement(Element element) {
        String tag = element.getTagName();
        // Common layout/ViewGroup tags
        return tag.equals("LinearLayout")
                || tag.equals("RelativeLayout")
                || tag.equals("FrameLayout")
                || tag.equals("TableLayout")
                || tag.equals("GridLayout")
                || tag.equals("ConstraintLayout")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("ListView")
                || tag.equals("GridView")
                || tag.equals("ViewGroup")
                || tag.equals("include")
                || tag.endsWith("Layout")
                || tag.contains(".");
    }
}