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
            "layout_alignRight", "layout_alignEnd",
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "toLeftOf", "toStartOf",
            "toRightOf", "toEndOf"
    };

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
        boolean hasLeft = element.hasAttributeNS(ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(ANDROID_URI, rightAttr);

        if (hasLeft && !hasRight) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, leftAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    "When you define `" + leftAttr + "` you should probably also define `" + rightAttr + "` for right-to-left symmetry");
        } else if (hasRight && !hasLeft) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, rightAttr);
            context.report(ISSUE, attr, context.getLocation(attr),
                    "When you define `" + rightAttr + "` you should probably also define `" + leftAttr + "` for right-to-left symmetry");
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int dash = name.indexOf('-');
        if (dash != -1) {
            int v = name.indexOf('v', dash);
            if (v != -1) {
                try {
                    return Integer.parseInt(name.substring(v + 1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return 0;
    }
}