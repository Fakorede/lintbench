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
            Category.RTL, 6, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final String[] ATTRIBUTES = {
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd"
    };

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
        Attr right = element.getAttributeNodeNS(ANDROID_URI, rightAttr);

        if (left != null && right == null) {
            context.report(ISSUE, left, context.getLocation(left),
                    "When you define `" + leftAttr + "` you should probably also define `" + rightAttr + "` for right-to-left symmetry");
        } else if (right != null && left == null) {
            context.report(ISSUE, right, context.getLocation(right),
                    "When you define `" + rightAttr + "` you should probably also define `" + leftAttr + "` for right-to-left symmetry");
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static String convertOldToNew(String name) {
        return name.replace("Left", "Start").replace("Right", "End");
    }

    public static String convertNewToOld(String name) {
        return name.replace("Start", "Left").replace("End", "Right");
    }

    public static String convertToOppositeDirection(String name) {
        if (name.contains("Left")) {
            return name.replace("Left", "Right");
        } else if (name.contains("Right")) {
            return name.replace("Right", "Left");
        } else if (name.contains("Start")) {
            return name.replace("Start", "End");
        } else if (name.contains("End")) {
            return name.replace("End", "Start");
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf('-');
        if (index != -1 && index < name.length() - 1) {
            String qualifier = name.substring(index + 1);
            if (qualifier.startsWith("v")) {
                try {
                    return Integer.parseInt(qualifier.substring(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 1;
    }
}