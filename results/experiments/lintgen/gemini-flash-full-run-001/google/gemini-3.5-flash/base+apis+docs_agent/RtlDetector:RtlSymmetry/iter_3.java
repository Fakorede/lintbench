package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue SYMMETRY = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "If you specify padding or margin on the left side of a layout, you should " +
        "probably also specify padding on the right side (and vice versa) for " +
        "right-to-left layout symmetry.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    private void checkSymmetry(XmlContext context, Element element, String left, String right) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, left);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, right);
        if (hasLeft && !hasRight) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, left);
            context.report(SYMMETRY, attr, context.getLocation(attr),
                String.format("When you define `%1$s` you should also define `%2$s` for symmetry", left, right));
        } else if (hasRight && !hasLeft) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, right);
            context.report(SYMMETRY, attr, context.getLocation(attr),
                String.format("When you define `%1$s` you should also define `%2$s` for symmetry", right, left));
        }
    }
}