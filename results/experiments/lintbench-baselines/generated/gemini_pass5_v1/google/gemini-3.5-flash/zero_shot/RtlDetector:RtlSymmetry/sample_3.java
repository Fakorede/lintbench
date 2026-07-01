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
        checkPair(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING);
        checkPair(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING);
        checkPair(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN);
        checkPair(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN);
    }

    private void checkPair(XmlContext context, Element element, String attr1, String attr2, String allAttr) {
        if (allAttr != null && element.hasAttributeNS(SdkConstants.ANDROID_URI, allAttr)) {
            return;
        }
        boolean has1 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr1);
        boolean has2 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr2);
        if (has1 && !has2) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr1);
            context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("When you define `%s`, you should also define `%s` for symmetry", attr1, attr2));
        } else if (has2 && !has1) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr2);
            context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("When you define `%s`, you should also define `%s` for symmetry", attr2, attr1));
        }
    }
}