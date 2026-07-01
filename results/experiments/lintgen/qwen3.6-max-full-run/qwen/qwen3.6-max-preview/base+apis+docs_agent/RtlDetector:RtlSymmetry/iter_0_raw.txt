package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL, 6, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr) {
        String ns = SdkConstants.ANDROID_URI;
        boolean hasLeft = element.hasAttributeNS(ns, leftAttr);
        boolean hasRight = element.hasAttributeNS(ns, rightAttr);

        if (hasLeft && !hasRight) {
            Attr attr = element.getAttributeNodeNS(ns, leftAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    "When you define `" + leftAttr + "` you should probably also define `" + rightAttr + "` for right-to-left symmetry");
        } else if (hasRight && !hasLeft) {
            Attr attr = element.getAttributeNodeNS(ns, rightAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    "When you define `" + rightAttr + "` you should probably also define `" + leftAttr + "` for right-to-left symmetry");
        }
    }
}