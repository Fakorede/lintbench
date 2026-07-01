package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element,
                SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END,
                SdkConstants.ATTR_PADDING);

        checkSymmetry(context, element,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END,
                SdkConstants.ATTR_LAYOUT_MARGIN);
    }

    private void checkSymmetry(XmlContext context, Element element,
                               String leftAttr, String rightAttr,
                               String startAttr, String endAttr,
                               String allAttr) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, allAttr)) {
            return;
        }

        Attr left = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, leftAttr);
        Attr start = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, startAttr);
        Attr right = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, rightAttr);
        Attr end = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, endAttr);

        boolean hasLeft = left != null || start != null;
        boolean hasRight = right != null || end != null;

        if (hasLeft && !hasRight) {
            Attr attr = left != null ? left : start;
            String opposite = (left != null) ? rightAttr : endAttr;
            context.report(ISSUE, context.getLocation(attr),
                    "When you define `" + attr.getLocalName() + "` you should probably also define `" + opposite + "` for right-to-left symmetry");
        } else if (hasRight && !hasLeft) {
            Attr attr = right != null ? right : end;
            String opposite = (right != null) ? leftAttr : startAttr;
            context.report(ISSUE, context.getLocation(attr),
                    "When you define `" + attr.getLocalName() + "` you should probably also define `" + opposite + "` for right-to-left symmetry");
        }
    }
}