package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should probably also specify padding on the right side (and vice versa) for right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_PADDING_HORIZONTAL = "paddingHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasAttribute(element, ATTR_PADDING_HORIZONTAL)) {
            checkSymmetric(context, element, ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
        }

        if (!hasAttribute(element, ATTR_LAYOUT_MARGIN_HORIZONTAL)) {
            checkSymmetric(context, element, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT);
        }
    }

    private static void checkSymmetric(XmlContext context, Element element,
            String leftAttr, String rightAttr) {
        boolean hasLeft = hasAttribute(element, leftAttr);
        boolean hasRight = hasAttribute(element, rightAttr);

        if (hasLeft && !hasRight) {
            report(context, element, leftAttr, rightAttr);
        } else if (hasRight && !hasLeft) {
            report(context, element, rightAttr, leftAttr);
        }
    }

    private static void report(XmlContext context, Element element,
            String specifiedAttr, String missingAttr) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, specifiedAttr);
        if (attr == null) {
            return;
        }
        String message = "To support right-to-left layouts, consider adding `"
                + missingAttr + "` alongside `" + specifiedAttr + "`";
        context.report(ISSUE, attr, context.getLocation(attr), message);
    }

    private static boolean hasAttribute(Element element, String name) {
        return element.hasAttributeNS(ANDROID_URI, name);
    }
}