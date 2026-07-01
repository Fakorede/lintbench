package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL, 6, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, "paddingLeft", "paddingRight");
        checkSymmetry(context, element, "layout_marginLeft", "layout_marginRight");
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr) {
        Attr left = element.getAttributeNodeNS(ANDROID_URI, leftAttr);
        if (left == null) {
            left = element.getAttributeNode("android:" + leftAttr);
        }

        Attr right = element.getAttributeNodeNS(ANDROID_URI, rightAttr);
        if (right == null) {
            right = element.getAttributeNode("android:" + rightAttr);
        }

        if (left != null && right == null) {
            context.report(ISSUE, left, context.getLocation(left),
                    "When you define `" + leftAttr + "` you should probably also define `" + rightAttr + "` for right-to-left symmetry");
        } else if (right != null && left == null) {
            context.report(ISSUE, right, context.getLocation(right),
                    "When you define `" + rightAttr + "` you should probably also define `" + leftAttr + "` for right-to-left symmetry");
        }
    }
}