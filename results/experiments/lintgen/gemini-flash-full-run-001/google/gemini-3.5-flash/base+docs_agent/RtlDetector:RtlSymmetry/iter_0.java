package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
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
            Scope.LAYOUT_RESOURCE_FILES
        )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        checkSymmetry(context, element, "paddingLeft", "paddingRight");
        checkSymmetry(context, element, "layout_marginLeft", "layout_marginRight");
        checkSymmetry(context, element, "paddingStart", "paddingEnd");
        checkSymmetry(context, element, "layout_marginStart", "layout_marginEnd");
    }

    private void checkSymmetry(XmlContext context, Element element, String left, String right) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, left);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, right);

        if (hasLeft != hasRight) {
            String present = hasLeft ? left : right;
            String missing = hasLeft ? right : left;
            context.report(
                ISSUE,
                element,
                context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, present)),
                "To support right-to-left layouts, when you define `" + present + "` you should also define `" + missing + "` for symmetry"
            );
        }
    }
}