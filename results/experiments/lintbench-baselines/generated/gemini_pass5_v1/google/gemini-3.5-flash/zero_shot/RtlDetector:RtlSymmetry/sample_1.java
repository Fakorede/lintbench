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
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.BIDI,
            5,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check padding symmetry
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PADDING)) {
            checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
            checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        }

        // Check margin symmetry
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN)) {
            checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
            checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
        }
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, rightAttr);

        if (hasLeft != hasRight) {
            Attr presentAttr;
            String missingAttr;
            if (hasLeft) {
                presentAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, leftAttr);
                missingAttr = rightAttr;
            } else {
                presentAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, rightAttr);
                missingAttr = leftAttr;
            }

            if (presentAttr != null) {
                context.report(
                        ISSUE,
                        presentAttr,
                        context.getLocation(presentAttr),
                        String.format("When you define `%1$s`, you should also define `%2$s` for symmetry",
                                presentAttr.getLocalName(), missingAttr)
                );
            }
        }
    }
}