package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class RtlDetector extends LayoutDetector {

    private static final String PADDING_HORIZONTAL = "paddingHorizontal";
    private static final String LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "To support right-to-left (RTL) layouts, when you specify a padding or margin on "
                    + "one horizontal side you should usually specify it on the other side as well. "
                    + "If the values are meant to be identical, consider using `padding`/`layout_margin` "
                    + "or `paddingHorizontal`/`layout_marginHorizontal`.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT, ATTR_PADDING, PADDING_HORIZONTAL);
        checkSymmetry(context, element, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN, LAYOUT_MARGIN_HORIZONTAL);
    }

    private static void checkSymmetry(XmlContext context, Element element,
            String leftAttr, String rightAttr, String allAttr, String horizontalAttr) {
        boolean hasLeft = hasAttr(element, leftAttr);
        boolean hasRight = hasAttr(element, rightAttr);
        boolean hasAll = hasAttr(element, allAttr);
        boolean hasHorizontal = hasAttr(element, horizontalAttr);

        if (hasAll || hasHorizontal) {
            return;
        }

        if (hasLeft && !hasRight) {
            reportMissing(context, element, leftAttr, rightAttr);
        } else if (hasRight && !hasLeft) {
            reportMissing(context, element, rightAttr, leftAttr);
        }
    }

    private static boolean hasAttr(Element element, String localName) {
        return element.hasAttributeNS(ANDROID_URI, localName);
    }

    private static void reportMissing(XmlContext context, Element element,
            String presentAttr, String missingAttr) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, presentAttr);
        if (attr == null) {
            return;
        }
        context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "To maintain RTL symmetry, when you define `"
                        + presentAttr
                        + "` you should also define `"
                        + missingAttr
                        + "`"
        );
    }
}