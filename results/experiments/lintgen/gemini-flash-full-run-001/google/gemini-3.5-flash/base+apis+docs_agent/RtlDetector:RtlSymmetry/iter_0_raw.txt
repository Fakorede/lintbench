package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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
        // Check padding symmetry
        boolean hasLeftPadding = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_LEFT);
        boolean hasRightPadding = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_RIGHT);
        boolean hasStartPadding = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_START);
        boolean hasEndPadding = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_END);

        if (hasLeftPadding && !hasRightPadding) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_LEFT);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `paddingLeft` you should also define `paddingRight` for symmetry");
        } else if (hasRightPadding && !hasLeftPadding) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_RIGHT);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `paddingRight` you should also define `paddingLeft` for symmetry");
        }

        if (hasStartPadding && !hasEndPadding) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_START);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `paddingStart` you should also define `paddingEnd` for symmetry");
        } else if (hasEndPadding && !hasStartPadding) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING_END);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `paddingEnd` you should also define `paddingStart` for symmetry");
        }

        // Check margin symmetry
        boolean hasLeftMargin = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
        boolean hasRightMargin = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        boolean hasStartMargin = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START);
        boolean hasEndMargin = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END);

        if (hasLeftMargin && !hasRightMargin) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `layout_marginLeft` you should also define `layout_marginRight` for symmetry");
        } else if (hasRightMargin && !hasLeftMargin) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `layout_marginRight` you should also define `layout_marginLeft` for symmetry");
        }

        if (hasStartMargin && !hasEndMargin) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `layout_marginStart` you should also define `layout_marginEnd` for symmetry");
        } else if (hasEndMargin && !hasStartMargin) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END);
            context.report(ISSUE, attr, context.getLocation(attr), "When you define `layout_marginEnd` you should also define `layout_marginStart` for symmetry");
        }
    }
}