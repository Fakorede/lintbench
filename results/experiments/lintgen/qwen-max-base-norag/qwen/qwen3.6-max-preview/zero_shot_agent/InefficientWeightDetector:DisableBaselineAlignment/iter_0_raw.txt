package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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
            "When a `LinearLayout` is used to distribute the space proportionally between nested layouts, " +
            "the baseline alignment property should be turned off to make the layout computation faster.",
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
        String baselineAligned = element.getAttributeNS(ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED);
        if (SdkConstants.VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasWeight = false;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttributeNS(ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT)) {
                    hasWeight = true;
                    break;
                }
            }
        }

        if (hasWeight) {
            LintFix fix = fix()
                    .set(ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED, SdkConstants.VALUE_FALSE)
                    .build();
            context.report(ISSUE, element, context.getLocation(element),
                    "Set android:baselineAligned=\"false\" on this LinearLayout for better performance", fix);
        }
    }
}