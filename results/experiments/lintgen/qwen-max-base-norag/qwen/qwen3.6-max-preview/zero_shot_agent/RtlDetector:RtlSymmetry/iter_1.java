package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL, 5, Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
        Attr left = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, leftAttr);
        Attr right = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, rightAttr);

        if (left != null && right == null) {
            context.report(ISSUE, context.getLocation(left),
                    "When you define `" + leftAttr + "` you should probably also define `" + rightAttr + "` for right-to-left symmetry");
        } else if (right != null && left == null) {
            context.report(ISSUE, context.getLocation(right),
                    "When you define `" + rightAttr + "` you should probably also define `" + leftAttr + "` for right-to-left symmetry");
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static String convertOldToNew(String name) {
        if (name.indexOf("Left") != -1) {
            return name.replace("Left", "Start");
        }
        if (name.indexOf("Right") != -1) {
            return name.replace("Right", "End");
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.indexOf("Start") != -1) {
            return name.replace("Start", "Left");
        }
        if (name.indexOf("End") != -1) {
            return name.replace("End", "Right");
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.indexOf("Left") != -1) {
            return name.replace("Left", "Right");
        }
        if (name.indexOf("Right") != -1) {
            return name.replace("Right", "Left");
        }
        if (name.indexOf("Start") != -1) {
            return name.replace("Start", "End");
        }
        if (name.indexOf("End") != -1) {
            return name.replace("End", "Start");
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int dash = name.indexOf('-');
        if (dash != -1) {
            int vIndex = name.indexOf('v', dash);
            if (vIndex != -1) {
                try {
                    return Integer.parseInt(name.substring(vIndex + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }
}