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

    public static final Issue SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    private void checkSymmetry(XmlContext context, Element element, String attr1, String attr2) {
        boolean hasAttr1 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr1);
        boolean hasAttr2 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr2);
        if (hasAttr1 && !hasAttr2) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr1);
            if (attribute != null) {
                context.report(SYMMETRY, attribute, context.getLocation(attribute),
                        String.format("To support right-to-left layouts, when you define `%1$s` you should also define `%2$s`", attr1, attr2));
            }
        } else if (hasAttr2 && !hasAttr1) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr2);
            if (attribute != null) {
                context.report(SYMMETRY, attribute, context.getLocation(attribute),
                        String.format("To support right-to-left layouts, when you define `%1$s` you should also define `%2$s`", attr2, attr1));
            }
        }
    }
}