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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = {
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "layout_alignParentLeft", "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart",
            "layout_alignRight", "layout_alignEnd",
            "layout_toLeftOf", "layout_toStartOf",
            "layout_toRightOf", "layout_toEndOf",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd"
    };

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        return name.replace("Left", "Start").replace("Right", "End");
    }

    public static String convertNewToOld(String name) {
        return name.replace("Start", "Left").replace("End", "Right");
    }

    public static String convertToOppositeDirection(String name) {
        String res = name;
        if (res.contains("Left")) {
            res = res.replace("Left", "Right");
        } else if (res.contains("Right")) {
            res = res.replace("Right", "Left");
        }
        if (res.contains("Start")) {
            res = res.replace("Start", "End");
        } else if (res.contains("End")) {
            res = res.replace("End", "Start");
        }
        return res;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, "paddingLeft", "paddingRight");
        checkSymmetry(context, element, "paddingStart", "paddingEnd");
        checkSymmetry(context, element, "layout_marginLeft", "layout_marginRight");
        checkSymmetry(context, element, "layout_marginStart", "layout_marginEnd");
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr) {
        Attr left = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, leftAttr);
        Attr right = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, rightAttr);

        if (left != null && right == null) {
            String message = String.format(
                    "When you define `android:%1$s` you should probably also define `android:%2$s` for right-to-left symmetry",
                    leftAttr, rightAttr);
            context.report(ISSUE, context.getLocation(left), message);
        } else if (left == null && right != null) {
            String message = String.format(
                    "When you define `android:%1$s` you should probably also define `android:%2$s` for right-to-left symmetry",
                    rightAttr, leftAttr);
            context.report(ISSUE, context.getLocation(right), message);
        }
    }
}