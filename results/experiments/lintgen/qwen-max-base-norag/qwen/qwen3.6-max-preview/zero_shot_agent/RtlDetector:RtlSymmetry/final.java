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
import java.util.Arrays;
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
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "marginLeft", "marginStart",
            "marginRight", "marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
            "toLeftOf", "toStartOf",
            "toRightOf", "toEndOf",
            "layout_alignParentLeft", "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart",
            "layout_alignRight", "layout_alignEnd",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "layout_toLeftOf", "layout_toStartOf",
            "layout_toRightOf", "layout_toEndOf"
    };

    public static boolean isRtlAttributeName(String name) {
        return name != null && (name.contains("Start") || name.contains("End"));
    }

    public static String convertOldToNew(String name) {
        if (name == null) return null;
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name == null) return null;
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name == null) return null;
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

    public static int getFolderVersion(File file) {
        if (file == null) return 1;
        String name = file.getName();
        int index = name.indexOf('-');
        if (index != -1) {
            int vIndex = name.indexOf('v', index);
            if (vIndex != -1) {
                try {
                    return Integer.parseInt(name.substring(vIndex + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 1;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("paddingLeft", "paddingRight", "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        String opposite = null;
        if ("paddingLeft".equals(name)) {
            opposite = "paddingRight";
        } else if ("paddingRight".equals(name)) {
            opposite = "paddingLeft";
        } else if ("layout_marginLeft".equals(name)) {
            opposite = "layout_marginRight";
        } else if ("layout_marginRight".equals(name)) {
            opposite = "layout_marginLeft";
        }

        if (opposite != null) {
            Element element = attribute.getOwnerElement();
            Attr oppositeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, opposite);
            if (oppositeAttr == null) {
                oppositeAttr = element.getAttributeNode(opposite);
            }
            if (oppositeAttr == null) {
                context.report(ISSUE, context.getLocation(attribute),
                        "When you define `" + name + "` you should probably also define `" + opposite + "` for right-to-left symmetry");
            }
        }
    }
}