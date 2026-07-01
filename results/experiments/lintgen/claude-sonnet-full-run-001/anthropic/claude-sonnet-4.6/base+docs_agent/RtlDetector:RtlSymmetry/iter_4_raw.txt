package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.NamedNodeMap;

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
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final String[] ATTRIBUTES = new String[] {
            "paddingLeft",          "paddingRight",
            "paddingStart",         "paddingEnd",
            "layout_marginLeft",    "layout_marginRight",
            "layout_marginStart",   "layout_marginEnd",
            "layout_alignParentLeft",  "layout_alignParentRight",
            "layout_alignParentStart", "layout_alignParentEnd",
            "layout_alignLeft",     "layout_alignRight",
            "layout_alignStart",    "layout_alignEnd",
            "layout_toLeftOf",      "layout_toRightOf",
            "layout_toStartOf",     "layout_toEndOf",
            "drawableLeft",         "drawableRight",
            "drawableStart",        "drawableEnd",
    };

    /**
     * Given a left/right/start/end attribute name, returns the opposite direction attribute name.
     * For example, "paddingLeft" -> "paddingRight", "paddingRight" -> "paddingLeft",
     * "paddingStart" -> "paddingEnd", "paddingEnd" -> "paddingStart".
     */
    @Nullable
    public static String convertToOppositeDirection(@NonNull String attribute) {
        // Handle Left <-> Right
        if (attribute.endsWith("Left")) {
            String base = attribute.substring(0, attribute.length() - "Left".length());
            return base + "Right";
        }
        if (attribute.endsWith("Right")) {
            String base = attribute.substring(0, attribute.length() - "Right".length());
            return base + "Left";
        }
        // Handle Start <-> End
        if (attribute.endsWith("Start")) {
            String base = attribute.substring(0, attribute.length() - "Start".length());
            return base + "End";
        }
        if (attribute.endsWith("End")) {
            String base = attribute.substring(0, attribute.length() - "End".length());
            return base + "Start";
        }
        return null;
    }

    public static boolean isRtlAttributeName(@NonNull String attribute) {
        return attribute.endsWith("Start") || attribute.endsWith("End");
    }

    @Nullable
    public static String convertOldToNew(@NonNull String attribute) {
        // Convert Left -> Start, Right -> End
        if (attribute.endsWith("Left")) {
            String base = attribute.substring(0, attribute.length() - "Left".length());
            return base + "Start";
        }
        if (attribute.endsWith("Right")) {
            String base = attribute.substring(0, attribute.length() - "Right".length());
            return base + "End";
        }
        return null;
    }

    @Nullable
    public static String convertNewToOld(@NonNull String attribute) {
        // Convert Start -> Left, End -> Right
        if (attribute.endsWith("Start")) {
            String base = attribute.substring(0, attribute.length() - "Start".length());
            return base + "Left";
        }
        if (attribute.endsWith("End")) {
            String base = attribute.substring(0, attribute.length() - "End".length());
            return base + "Right";
        }
        return null;
    }

    public static int getFolderVersion(@NonNull File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index == -1) {
            File parent = folder.getParentFile();
            if (parent != null) {
                name = parent.getName();
                index = name.indexOf("-v");
            }
        }
        if (index != -1) {
            String versionStr = name.substring(index + 2);
            int end = versionStr.length();
            for (int i = 0; i < versionStr.length(); i++) {
                char c = versionStr.charAt(i);
                if (!Character.isDigit(c)) {
                    end = i;
                    break;
                }
            }
            versionStr = versionStr.substring(0, end);
            if (!versionStr.isEmpty()) {
                try {
                    return Integer.parseInt(versionStr);
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return -1;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        boolean hasPaddingLeft = false;
        boolean hasPaddingRight = false;
        boolean hasPaddingStart = false;
        boolean hasPaddingEnd = false;
        boolean hasMarginLeft = false;
        boolean hasMarginRight = false;
        boolean hasMarginStart = false;
        boolean hasMarginEnd = false;

        Attr paddingLeftAttr = null;
        Attr paddingRightAttr = null;
        Attr paddingStartAttr = null;
        Attr paddingEndAttr = null;
        Attr marginLeftAttr = null;
        Attr marginRightAttr = null;
        Attr marginStartAttr = null;
        Attr marginEndAttr = null;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (!ANDROID_NS.equals(ns)) {
                continue;
            }
            String localName = attr.getLocalName();
            if (localName == null) {
                continue;
            }
            switch (localName) {
                case "paddingLeft":
                    hasPaddingLeft = true;
                    paddingLeftAttr = attr;
                    break;
                case "paddingRight":
                    hasPaddingRight = true;
                    paddingRightAttr = attr;
                    break;
                case "paddingStart":
                    hasPaddingStart = true;
                    paddingStartAttr = attr;
                    break;
                case "paddingEnd":
                    hasPaddingEnd = true;
                    paddingEndAttr = attr;
                    break;
                case "layout_marginLeft":
                    hasMarginLeft = true;
                    marginLeftAttr = attr;
                    break;
                case "layout_marginRight":
                    hasMarginRight = true;
                    marginRightAttr = attr;
                    break;
                case "layout_marginStart":
                    hasMarginStart = true;
                    marginStartAttr = attr;
                    break;
                case "layout_marginEnd":
                    hasMarginEnd = true;
                    marginEndAttr = attr;
                    break;
                default:
                    break;
            }
        }

        if (hasPaddingLeft && !hasPaddingRight) {
            context.report(ISSUE, element, context.getLocation(paddingLeftAttr),
                    "Should specify `android:paddingRight` as well as `android:paddingLeft` " +
                    "for right-to-left layout symmetry");
        } else if (hasPaddingRight && !hasPaddingLeft) {
            context.report(ISSUE, element, context.getLocation(paddingRightAttr),
                    "Should specify `android:paddingLeft` as well as `android:paddingRight` " +
                    "for right-to-left layout symmetry");
        }

        if (hasPaddingStart && !hasPaddingEnd) {
            context.report(ISSUE, element, context.getLocation(paddingStartAttr),
                    "Should specify `android:paddingEnd` as well as `android:paddingStart` " +
                    "for right-to-left layout symmetry");
        } else if (hasPaddingEnd && !hasPaddingStart) {
            context.report(ISSUE, element, context.getLocation(paddingEndAttr),
                    "Should specify `android:paddingStart` as well as `android:paddingEnd` " +
                    "for right-to-left layout symmetry");
        }

        if (hasMarginLeft && !hasMarginRight) {
            context.report(ISSUE, element, context.getLocation(marginLeftAttr),
                    "Should specify `android:layout_marginRight` as well as " +
                    "`android:layout_marginLeft` for right-to-left layout symmetry");
        } else if (hasMarginRight && !hasMarginLeft) {
            context.report(ISSUE, element, context.getLocation(marginRightAttr),
                    "Should specify `android:layout_marginLeft` as well as " +
                    "`android:layout_marginRight` for right-to-left layout symmetry");
        }

        if (hasMarginStart && !hasMarginEnd) {
            context.report(ISSUE, element, context.getLocation(marginStartAttr),
                    "Should specify `android:layout_marginEnd` as well as " +
                    "`android:layout_marginStart` for right-to-left layout symmetry");
        } else if (hasMarginEnd && !hasMarginStart) {
            context.report(ISSUE, element, context.getLocation(marginEndAttr),
                    "Should specify `android:layout_marginStart` as well as " +
                    "`android:layout_marginEnd` for right-to-left layout symmetry");
        }
    }
}