package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.BI_DIRECTIONAL,
            5,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT, "padding");
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, "margin");
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END, "padding");
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END, "margin");
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr, String type) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, rightAttr);

        if (hasLeft != hasRight) {
            String presentAttr = hasLeft ? leftAttr : rightAttr;
            String missingAttr = hasLeft ? rightAttr : leftAttr;
            Attr attributeNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, presentAttr);
            
            String message = String.format(
                    "To ensure symmetrical %s, when you define `%s` you should also define `%s`",
                    type, presentAttr, missingAttr);
            
            context.report(ISSUE, element, context.getLocation(attributeNode != null ? attributeNode : element), message);
        }
    }
}