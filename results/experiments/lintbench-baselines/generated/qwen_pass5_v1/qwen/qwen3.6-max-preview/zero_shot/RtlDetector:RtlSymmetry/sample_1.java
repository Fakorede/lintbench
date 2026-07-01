package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.tools.lint.detector.api.Detector.ALL;

public class RtlDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            4,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr paddingLeft = element.getAttributeNodeNS(ANDROID_URI, ATTR_PADDING_LEFT);
        Attr paddingRight = element.getAttributeNodeNS(ANDROID_URI, ATTR_PADDING_RIGHT);

        if (paddingLeft != null && paddingRight == null) {
            context.report(ISSUE, paddingLeft, context.getLocation(paddingLeft),
                    "When you define `paddingLeft` you should probably also define `paddingRight` for right-to-left symmetry");
        } else if (paddingRight != null && paddingLeft == null) {
            context.report(ISSUE, paddingRight, context.getLocation(paddingRight),
                    "When you define `paddingRight` you should probably also define `paddingLeft` for right-to-left symmetry");
        }

        Attr marginLeft = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT);
        Attr marginRight = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT);

        if (marginLeft != null && marginRight == null) {
            context.report(ISSUE, marginLeft, context.getLocation(marginLeft),
                    "When you define `layout_marginLeft` you should probably also define `layout_marginRight` for right-to-left symmetry");
        } else if (marginRight != null && marginLeft == null) {
            context.report(ISSUE, marginRight, context.getLocation(marginRight),
                    "When you define `layout_marginRight` you should probably also define `layout_marginLeft` for right-to-left symmetry");
        }
    }
}