package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Pairs of (old/left-right attribute, new/start-end attribute).
     * Each pair is: old attribute at index i, new attribute at index i+1.
     */
    public static final String[] ATTRIBUTES = new String[] {
            // padding
            "paddingLeft",           "paddingStart",
            "paddingRight",          "paddingEnd",
            // layout_margin
            "layout_marginLeft",     "layout_marginStart",
            "layout_marginRight",    "layout_marginEnd",
            // drawablePadding / compound drawables
            "drawableLeft",          "drawableStart",
            "drawableRight",         "drawableEnd",
            // RelativeLayout alignment
            "layout_alignParentLeft",  "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft",        "layout_alignStart",
            "layout_alignRight",       "layout_alignEnd",
            "layout_toLeftOf",         "layout_toStartOf",
            "layout_toRightOf",        "layout_toEndOf",
    };

    // Map from old (left/right) attribute name to new (start/end) attribute name
    private static final Map<String, String> OLD_TO_NEW = new HashMap<>();
    // Map from new (start/end) attribute name to old (left/right) attribute name
    private static final Map<String, String> NEW_TO_OLD = new HashMap<>();
    // Map for flipping direction within the same "family" (left<->right, start<->end)
    private static final Map<String, String> FLIP_MAP = new HashMap<>();

    static {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String oldAttr = ATTRIBUTES[i];
            String newAttr = ATTRIBUTES[i + 1];
            OLD_TO_NEW.put(oldAttr, newAttr);
            NEW_TO_OLD.put(newAttr, oldAttr);
        }

        // Build flip map: left<->right and start<->end variants
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String leftAttr = ATTRIBUTES[i];       // e.g. "paddingLeft"
            String rightAttr;
            // Find the corresponding "right" attribute
            // The "left" attributes are at even indices 0,2,4,...
            // "right" attributes are at even indices 1,3,5,... wait no:
            // Actually pairs are (left, start) and (right, end) interleaved
            // Let me re-examine: index 0=paddingLeft, 1=paddingStart, 2=paddingRight, 3=paddingEnd
            // So left is at 0, right is at 2 for padding group
            // This is tricky - let me just build flip map by string manipulation
        }

        // Build flip map by string replacement
        // For old-style: Left <-> Right
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String oldAttr = ATTRIBUTES[i]; // left or right variant
            // find its opposite
            String opposite = flipOldDirection(oldAttr);
            if (opposite != null) {
                FLIP_MAP.put(oldAttr, opposite);
            }
        }
        // For new-style: Start <-> End
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            String newAttr = ATTRIBUTES[i]; // start or end variant
            String opposite = flipNewDirection(newAttr);
            if (opposite != null) {
                FLIP_MAP.put(newAttr, opposite);
            }
        }
    }

    private static String flipOldDirection(String attr) {
        if (attr.contains("Left")) {
            return attr.replace("Left", "Right");
        } else if (attr.contains("Right")) {
            return attr.replace("Right", "Left");
        }
        return null;
    }

    private static String flipNewDirection(String attr) {
        if (attr.contains("Start")) {
            return attr.replace("Start", "End");
        } else if (attr.contains("End")) {
            return attr.replace("End", "Start");
        }
        return null;
    }

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * Returns true if the given attribute name is an RTL-aware (start/end) attribute.
     */
    public static boolean isRtlAttributeName(String name) {
        return NEW_TO_OLD.containsKey(name);
    }

    /**
     * Converts an old (left/right) attribute name to the new (start/end) equivalent.
     * Returns null if not found.
     */
    public static String convertOldToNew(String oldAttribute) {
        return OLD_TO_NEW.get(oldAttribute);
    }

    /**
     * Converts a new (start/end) attribute name to the old (left/right) equivalent.
     * Returns null if not found.
     */
    public static String convertNewToOld(String newAttribute) {
        return NEW_TO_OLD.get(newAttribute);
    }

    /**
     * Converts an attribute to its opposite direction counterpart.
     * Left -> Right, Right -> Left, Start -> End, End -> Start.
     */
    public static String convertToOppositeDirection(String attribute) {
        String result = FLIP_MAP.get(attribute);
        if (result != null) {
            return result;
        }
        // Fallback: try string manipulation
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    /**
     * Returns the API version encoded in the folder name (e.g. "layout-v17" -> 17),
     * or -1 if not present.
     */
    public static int getFolderVersion(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return -1;
        }
        String folderName = parent.getName();
        int index = folderName.indexOf('-');
        while (index != -1) {
            String segment = folderName.substring(index + 1);
            if (segment.startsWith("v")) {
                try {
                    int nextDash = segment.indexOf('-');
                    String versionStr = nextDash == -1 ? segment.substring(1) : segment.substring(1, nextDash);
                    return Integer.parseInt(versionStr);
                } catch (NumberFormatException ignore) {
                }
            }
            index = folderName.indexOf('-', index + 1);
        }
        return -1;
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

        String oppositeAttrName;
        switch (localName) {
            case "paddingLeft":
                oppositeAttrName = "paddingRight";
                break;
            case "paddingRight":
                oppositeAttrName = "paddingLeft";
                break;
            case "layout_marginLeft":
                oppositeAttrName = "layout_marginRight";
                break;
            case "layout_marginRight":
                oppositeAttrName = "layout_marginLeft";
                break;
            default:
                return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Attr opposite = element.getAttributeNodeNS(ANDROID_NS, oppositeAttrName);
        if (opposite != null) {
            return;
        }

        String message = String.format(
                "When specifying `%1$s` attribute, you should probably also specify `%2$s` " +
                "for right-to-left layout symmetry",
                localName, oppositeAttrName);

        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}