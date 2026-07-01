package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

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
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart = null;

        if (name.endsWith("Left")) {
            counterpart = name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            counterpart = name.substring(0, name.length() - 5) + "Left";
        }

        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
            return;
        }

        String message = String.format(
                "When you define `%s` you should probably also define `%s` for right-to-left symmetry",
                name, counterpart);

        context.report(ISSUE, context.getLocation(attribute), message);
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        }
        if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        }
        if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        }
        if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        }
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf('-');
        while (index != -1) {
            int next = name.indexOf('-', index + 1);
            String qualifier = next == -1 ? name.substring(index + 1) : name.substring(index + 1, next);
            if (qualifier.startsWith("v")) {
                try {
                    return Integer.parseInt(qualifier.substring(1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            index = next;
        }
        return 1;
    }
}