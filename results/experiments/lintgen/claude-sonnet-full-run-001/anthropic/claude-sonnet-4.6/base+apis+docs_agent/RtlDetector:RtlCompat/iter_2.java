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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    // Pairs of (old/left-right attribute, new/start-end attribute)
    public static final String[] ATTRIBUTES = new String[] {
            // layout_ attributes
            "layout_alignParentLeft",   "layout_alignParentStart",
            "layout_alignParentRight",  "layout_alignParentEnd",
            "layout_alignLeft",         "layout_alignStart",
            "layout_alignRight",        "layout_alignEnd",
            "layout_marginLeft",        "layout_marginStart",
            "layout_marginRight",       "layout_marginEnd",
            "layout_toLeftOf",          "layout_toStartOf",
            "layout_toRightOf",         "layout_toEndOf",
            // non-layout_ attributes
            "paddingLeft",              "paddingStart",
            "paddingRight",             "paddingEnd",
            "drawableLeft",             "drawableStart",
            "drawableRight",            "drawableEnd",
    };

    private static final Map<String, String> OLD_TO_NEW = new HashMap<String, String>();
    private static final Map<String, String> NEW_TO_OLD = new HashMap<String, String>();
    // Map from left/right to opposite direction (left<->right, start<->end)
    private static final Map<String, String> OPPOSITE = new HashMap<String, String>();

    static {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String old = ATTRIBUTES[i];
            String neu = ATTRIBUTES[i + 1];
            OLD_TO_NEW.put(old, neu);
            NEW_TO_OLD.put(neu, old);
        }

        // Build opposite direction map for both old (left/right) and new (start/end) attributes
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String old = ATTRIBUTES[i];
            String neu = ATTRIBUTES[i + 1];
            // old[i] <-> old[i+1] would be left<->right pairs, but we need to find the
            // counterpart. The old attributes come in left/right pairs at consecutive even indices.
            // Actually let's build it differently: pair up left with right and start with end.
        }

        // Build opposite map: for each pair of (left-attr, right-attr) and (start-attr, end-attr)
        // We'll do it by scanning for Left/Right/Start/End suffixes/substrings
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String leftAttr = ATTRIBUTES[i];   // contains "Left" or similar
            String startAttr = ATTRIBUTES[i + 1]; // contains "Start" or similar

            // Find the corresponding right and end attributes
            String rightAttr = leftAttr.replace("Left", "Right");
            String endAttr = startAttr.replace("Start", "End");

            // left <-> right
            OPPOSITE.put(leftAttr, rightAttr);
            OPPOSITE.put(rightAttr, leftAttr);
            // start <-> end
            OPPOSITE.put(startAttr, endAttr);
            OPPOSITE.put(endAttr, startAttr);
        }
    }

    /**
     * Returns true if the given attribute name is an RTL-specific attribute
     * (i.e., uses Start/End rather than Left/Right).
     */
    public static boolean isRtlAttributeName(String name) {
        // Strip layout_ prefix for checking
        String localName = name;
        if (localName.startsWith("layout_")) {
            localName = localName.substring("layout_".length());
        }
        // Check if it ends with Start or End
        return localName.endsWith("Start") || localName.endsWith("End");
    }

    /**
     * Converts an old (left/right) attribute name to the new (start/end) equivalent.
     * Returns null if no mapping exists.
     */
    public static String convertOldToNew(String attribute) {
        // The ATTRIBUTES array uses full attribute names like "layout_alignParentLeft"
        // but tests call with short names like "alignParentLeft"
        // Try direct lookup first
        String result = OLD_TO_NEW.get(attribute);
        if (result != null) {
            return result;
        }
        // Try with layout_ prefix
        result = OLD_TO_NEW.get("layout_" + attribute);
        if (result != null) {
            // Strip layout_ prefix from result
            if (result.startsWith("layout_")) {
                return result.substring("layout_".length());
            }
            return result;
        }
        // Try direct string manipulation: Left -> Start, Right -> End
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        }
        if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return null;
    }

    /**
     * Converts a new (start/end) attribute name to the old (left/right) equivalent.
     * Returns null if no mapping exists.
     */
    public static String convertNewToOld(String attribute) {
        // Try direct lookup first
        String result = NEW_TO_OLD.get(attribute);
        if (result != null) {
            return result;
        }
        // Try with layout_ prefix
        result = NEW_TO_OLD.get("layout_" + attribute);
        if (result != null) {
            if (result.startsWith("layout_")) {
                return result.substring("layout_".length());
            }
            return result;
        }
        // Try direct string manipulation: Start -> Left, End -> Right
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        }
        if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return null;
    }

    /**
     * Converts an attribute to its opposite direction counterpart:
     * Left <-> Right, Start <-> End
     */
    public static String convertToOppositeDirection(String attribute) {
        // Try direct lookup
        String result = OPPOSITE.get(attribute);
        if (result != null) {
            return result;
        }
        // Try with layout_ prefix
        result = OPPOSITE.get("layout_" + attribute);
        if (result != null) {
            if (result.startsWith("layout_")) {
                return result.substring("layout_".length());
            }
            return result;
        }
        // Fallback: string manipulation
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        }
        if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        }
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        }
        if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    /**
     * Returns the API version encoded in the folder name (e.g., "layout-v17" returns 17),
     * or -1 if no version qualifier is present.
     */
    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index == -1) {
            // Also check for just the folder name itself if it's a file path
            // Try parent folder
            File parent = folder.getParentFile();
            if (parent != null) {
                name = parent.getName();
                index = name.indexOf("-v");
            }
        }
        if (index != -1) {
            String versionStr = name.substring(index + 2);
            // The version string might have more qualifiers after it
            int end = versionStr.length();
            for (int i = 0; i < versionStr.length(); i++) {
                char c = versionStr.charAt(i);
                if (!Character.isDigit(c)) {
                    end = i;
                    break;
                }
            }
            if (end > 0) {
                try {
                    return Integer.parseInt(versionStr.substring(0, end));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return -1;
    }

    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        // Only flag if minSdkVersion < 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 17) {
            return;
        }

        // Check whether the element also has a gravity or layout_gravity attribute
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                // Strip namespace prefix if present
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (ATTR_GRAVITY.equals(localName) || ATTR_LAYOUT_GRAVITY.equals(localName)) {
                hasGravity = true;
                break;
            }
        }

        if (!hasGravity) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17 (current min is " + minSdk + ") " +
                    "you must also specify `gravity` or `layout_gravity` when specifying " +
                    "`textAlignment`"
            );
        }
    }
}