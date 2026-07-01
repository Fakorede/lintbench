package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE_SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
                    "probably also specify padding on the right side (and vice versa) for " +
                    "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_PADDING_HORIZONTAL = "paddingHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                SdkConstants.ATTR_PADDING_LEFT,
                SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_PADDING_START,
                SdkConstants.ATTR_PADDING_END,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart;
        String all;
        String horizontal;

        if (SdkConstants.ATTR_PADDING_LEFT.equals(name)) {
            counterpart = SdkConstants.ATTR_PADDING_RIGHT;
            all = SdkConstants.ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (SdkConstants.ATTR_PADDING_RIGHT.equals(name)) {
            counterpart = SdkConstants.ATTR_PADDING_LEFT;
            all = SdkConstants.ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (SdkConstants.ATTR_PADDING_START.equals(name)) {
            counterpart = SdkConstants.ATTR_PADDING_END;
            all = SdkConstants.ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (SdkConstants.ATTR_PADDING_END.equals(name)) {
            counterpart = SdkConstants.ATTR_PADDING_START;
            all = SdkConstants.ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            counterpart = SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
            all = SdkConstants.ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            counterpart = SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
            all = SdkConstants.ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_START.equals(name)) {
            counterpart = SdkConstants.ATTR_LAYOUT_MARGIN_END;
            all = SdkConstants.ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_END.equals(name)) {
            counterpart = SdkConstants.ATTR_LAYOUT_MARGIN_START;
            all = SdkConstants.ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)
                && !element.hasAttributeNS(SdkConstants.ANDROID_URI, all)
                && !element.hasAttributeNS(SdkConstants.ANDROID_URI, horizontal)) {
            String message = String.format(
                    "When you define %1$s you should probably also define %2$s for right-to-left symmetry",
                    name, counterpart);
            context.report(ISSUE_SYMMETRY, attribute, context.getLocation(attribute), message);
        }
    }
}