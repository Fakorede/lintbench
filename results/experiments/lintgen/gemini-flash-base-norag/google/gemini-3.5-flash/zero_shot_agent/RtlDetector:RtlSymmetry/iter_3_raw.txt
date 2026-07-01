package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;

public class RtlDetector extends LayoutDetector {

    public static final Issue USE_START_END = Issue.create(
            "RtlHardcoded",
            "Using 'left'/'right' instead of 'start'/'end' attributes",
            "To support right-to-left layouts on L and higher, you should use start/end attributes.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue COMPATIBILITY = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility",
            "API 17 adds start/end attributes. If you support older APIs, you must also keep the left/right attributes.",
            Category.I18N,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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

    public static final Issue ENABLED = Issue.create(
            "RtlEnabled",
            "Using RTL attributes without enabling RTL support",
            "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in the manifest.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element);
    }

    private void checkSymmetry(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN);
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr, String allAttr) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, rightAttr);
        boolean hasAll = allAttr != null && element.hasAttributeNS(SdkConstants.ANDROID_URI, allAttr);

        if (hasAll) {
            return;
        }

        if (hasLeft != hasRight) {
            String missing = hasLeft ? rightAttr : leftAttr;
            String present = hasLeft ? leftAttr : rightAttr;
            context.report(
                    SYMMETRY,
                    element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, present)),
                    String.format("To support right-to-left layouts, when you define `android:%1$s` you should also define `android:%2$s`", present, missing)
            );
        }
    }
}