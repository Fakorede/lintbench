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
import org.w3c.dom.Document;
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
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * Array of old-to-new attribute name pairs (old at even indices, new at odd indices).
     * Each pair maps a left/right attribute to its start/end equivalent.
     */
    public static final String[] ATTRIBUTES = {
            // paddingLeft <-> paddingStart
            "paddingLeft", "paddingStart",
            // paddingRight <-> paddingEnd
            "paddingRight", "paddingEnd",
            // layout_marginLeft <-> layout_marginStart
            "layout_marginLeft", "layout_marginStart",
            // layout_marginRight <-> layout_marginEnd
            "layout_marginRight", "layout_marginEnd",
            // layout_alignParentLeft <-> layout_alignParentStart
            "layout_alignParentLeft", "layout_alignParentStart",
            // layout_alignParentRight <-> layout_alignParentEnd
            "layout_alignParentRight", "layout_alignParentEnd",
            // layout_alignLeft <-> layout_alignStart
            "layout_alignLeft", "layout_alignStart",
            // layout_alignRight <-> layout_alignEnd
            "layout_alignRight", "layout_alignEnd",
            // layout_toLeftOf <-> layout_toStartOf
            "layout_toLeftOf", "layout_toStartOf",
            // layout_toRightOf <-> layout_toEndOf
            "layout_toRightOf", "layout_toEndOf",
            // drawableLeft <-> drawableStart
            "drawableLeft", "drawableStart",
            // drawableRight <-> drawableEnd
            "drawableRight", "drawableEnd",
            // layout_gravity left/right <-> start/end handled separately
    };

    /**
     * Returns whether the given attribute name is an RTL (start/end) attribute.
     */
    public static boolean isRtlAttributeName(@NonNull String attribute) {
        return attribute.endsWith("Start") || attribute.endsWith("End");
    }

    /**
     * Converts an old (left/right) attribute name to the new (start/end) equivalent.
     * Returns null if no conversion is known.
     */
    @Nullable
    public static String convertOldToNew(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        // Handle non-prefixed names (without layout_ prefix) by checking suffix
        if (attribute.endsWith("Left")) {
            String base = attribute.substring(0, attribute.length() - 4);
            return base + "Start";
        } else if (attribute.endsWith("Right")) {
            String base = attribute.substring(0, attribute.length() - 5);
            return base + "End";
        }
        return null;
    }

    /**
     * Converts a new (start/end) attribute name to the old (left/right) equivalent.
     * Returns null if no conversion is known.
     */
    @Nullable
    public static String convertNewToOld(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        // Handle non-prefixed names by checking suffix
        if (attribute.endsWith("Start")) {
            String base = attribute.substring(0, attribute.length() - 5);
            return base + "Left";
        } else if (attribute.endsWith("End")) {
            String base = attribute.substring(0, attribute.length() - 3);
            return base + "Right";
        }
        return null;
    }

    /**
     * Converts an attribute to its opposite direction counterpart:
     * left <-> right, start <-> end.
     */
    @Nullable
    public static String convertToOppositeDirection(@NonNull String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Right";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Start";
        }
        return null;
    }

    /**
     * Returns the API version encoded in the folder name (e.g., "layout-v17" returns 17).
     * Returns -1 if no version qualifier is found.
     */
    public static int getFolderVersion(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return -1;
        }
        String folderName = parent.getName();
        int index = folderName.indexOf("-v");
        if (index == -1) {
            return -1;
        }
        String versionStr = folderName.substring(index + 2);
        // Strip any additional qualifiers after the version number
        int end = versionStr.length();
        for (int i = 0; i < versionStr.length(); i++) {
            char c = versionStr.charAt(i);
            if (!Character.isDigit(c)) {
                end = i;
                break;
            }
        }
        versionStr = versionStr.substring(0, end);
        if (versionStr.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(versionStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.XML;
    }

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
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        String counterpart = getSymmetricCounterpart(localName);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Check if the counterpart attribute exists (with namespace)
        Attr counterpartAttr = element.getAttributeNodeNS(ANDROID_NS, counterpart);
        if (counterpartAttr == null) {
            // Also check without namespace
            counterpartAttr = element.getAttributeNode("android:" + counterpart);
        }
        if (counterpartAttr == null) {
            // Counterpart is missing - report the issue
            String message = String.format(
                    "When specifying `%1$s` you should probably also specify `%2$s` " +
                    "for right-to-left layout symmetry",
                    localName, counterpart);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Nullable
    private static String getSymmetricCounterpart(@NonNull String attribute) {
        switch (attribute) {
            case "paddingLeft":
                return "paddingRight";
            case "paddingRight":
                return "paddingLeft";
            case "layout_marginLeft":
                return "layout_marginRight";
            case "layout_marginRight":
                return "layout_marginLeft";
            default:
                return null;
        }
    }
}