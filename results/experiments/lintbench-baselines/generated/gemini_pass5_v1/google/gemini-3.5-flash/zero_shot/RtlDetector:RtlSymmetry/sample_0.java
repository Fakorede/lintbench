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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, rightAttr);

        if (hasLeft && !hasRight) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, leftAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    String.format("To ensure symmetrical layouts, when you define `%s` you should also define `%s`", leftAttr, rightAttr));
        } else if (!hasLeft && hasRight) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, rightAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    String.format("To ensure symmetrical layouts, when you define `%s` you should also define `%s`", rightAttr, leftAttr));
        }
    }
}