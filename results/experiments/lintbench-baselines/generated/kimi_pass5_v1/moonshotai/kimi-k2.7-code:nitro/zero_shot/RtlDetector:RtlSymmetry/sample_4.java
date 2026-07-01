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

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkPair(context, element,
                SdkConstants.ATTR_PADDING_LEFT,
                SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_PADDING,
                "padding");

        checkPair(context, element,
                SdkConstants.ATTR_PADDING_START,
                SdkConstants.ATTR_PADDING_END,
                SdkConstants.ATTR_PADDING,
                "padding");

        checkPair(context, element,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN,
                "margin");

        checkPair(context, element,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_END,
                SdkConstants.ATTR_LAYOUT_MARGIN,
                "margin");
    }

    private static void checkPair(XmlContext context, Element element,
            String sideOne, String sideTwo, String globalAttr, String type) {
        boolean hasOne = hasAttr(element, sideOne);
        boolean hasTwo = hasAttr(element, sideTwo);
        if (!hasOne && !hasTwo) {
            return;
        }
        if (hasAttr(element, globalAttr)) {
            return;
        }

        if (hasOne && !hasTwo) {
            report(context, element, sideOne, sideTwo, type);
        } else if (hasTwo && !hasOne) {
            report(context, element, sideTwo, sideOne, type);
        }
    }

    private static void report(XmlContext context, Element element,
            String definedAttr, String missingAttr, String type) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, definedAttr);
        String message = String.format(
                "When you define %1$s on one side you should define %1$s on the other side for "
                        + "RTL symmetry (found %2$s but not %3$s)",
                type, definedAttr, missingAttr);
        context.report(ISSUE, attr != null ? attr : element,
                context.getLocation(attr != null ? attr : element), message);
    }

    private static boolean hasAttr(Element element, String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }
}