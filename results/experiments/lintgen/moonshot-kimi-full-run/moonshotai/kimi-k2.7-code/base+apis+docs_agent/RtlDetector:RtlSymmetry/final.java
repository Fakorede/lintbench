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

import java.io.File;
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

    public static final String[] ATTRIBUTES = new String[] {
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "layout_alignParentLeft", "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart",
            "layout_alignRight", "layout_alignEnd",
            "layout_toLeftOf", "layout_toStartOf",
            "layout_toRightOf", "layout_toEndOf",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
    };

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        if (!hasAttribute(element, "paddingHorizontal")) {
            checkSymmetric(context, element, "paddingLeft", "paddingRight");
        }

        if (!hasAttribute(element, "layout_marginHorizontal")) {
            checkSymmetric(context, element, "layout_marginLeft", "layout_marginRight");
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

    public static String convertOldToNew(String attr) {
        if (attr.endsWith("Left")) {
            return attr.substring(0, attr.length() - 4) + "Start";
        } else if (attr.endsWith("Right")) {
            return attr.substring(0, attr.length() - 5) + "End";
        }
        return attr;
    }

    public static String convertNewToOld(String attr) {
        if (attr.endsWith("Start")) {
            return attr.substring(0, attr.length() - 5) + "Left";
        } else if (attr.endsWith("End")) {
            return attr.substring(0, attr.length() - 3) + "Right";
        }
        return attr;
    }

    public static String convertToOppositeDirection(String attr) {
        if (attr.endsWith("Left")) {
            return attr.substring(0, attr.length() - 4) + "Right";
        } else if (attr.endsWith("Right")) {
            return attr.substring(0, attr.length() - 5) + "Left";
        } else if (attr.endsWith("Start")) {
            return attr.substring(0, attr.length() - 5) + "End";
        } else if (attr.endsWith("End")) {
            return attr.substring(0, attr.length() - 3) + "Start";
        }
        return attr;
    }

    public static boolean isRtlAttributeName(String attr) {
        return attr.endsWith("Start") || attr.endsWith("End");
    }

    public static int getFolderVersion(File file) {
        String path = file.getPath();
        String lastSegment = path;
        int sep = path.lastIndexOf(File.separatorChar);
        if (sep != -1) {
            lastSegment = path.substring(sep + 1);
        }
        String folderPath;
        if (lastSegment.contains(".") && !lastSegment.startsWith(".")) {
            folderPath = sep != -1 ? path.substring(0, sep) : path;
        } else {
            folderPath = path;
        }
        int index = folderPath.lastIndexOf("-v");
        if (index == -1) {
            return -1;
        }
        int start = index + 2;
        int end = start;
        while (end < folderPath.length() && Character.isDigit(folderPath.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(folderPath.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}