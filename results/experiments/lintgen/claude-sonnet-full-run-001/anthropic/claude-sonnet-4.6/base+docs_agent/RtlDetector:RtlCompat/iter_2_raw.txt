package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;

public class RtlDetector extends Detector implements XmlScanner {

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

    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final int RTL_API = 17;

    /**
     * Mapping from old (left/right) attribute names to new (start/end) attribute names.
     * Pairs of [oldAttribute, newAttribute].
     */
    public static final String[] ATTRIBUTES = new String[] {
            // paddingLeft <-> paddingStart
            "paddingLeft",              "paddingStart",
            // paddingRight <-> paddingEnd
            "paddingRight",             "paddingEnd",
            // layout_marginLeft <-> layout_marginStart
            "layout_marginLeft",        "layout_marginStart",
            // layout_marginRight <-> layout_marginEnd
            "layout_marginRight",       "layout_marginEnd",
            // layout_alignParentLeft <-> layout_alignParentStart
            "layout_alignParentLeft",   "layout_alignParentStart",
            // layout_alignParentRight <-> layout_alignParentEnd
            "layout_alignParentRight",  "layout_alignParentEnd",
            // layout_alignLeft <-> layout_alignStart
            "layout_alignLeft",         "layout_alignStart",
            // layout_alignRight <-> layout_alignEnd
            "layout_alignRight",        "layout_alignEnd",
            // layout_toLeftOf <-> layout_toStartOf
            "layout_toLeftOf",          "layout_toStartOf",
            // layout_toRightOf <-> layout_toEndOf
            "layout_toRightOf",         "layout_toEndOf",
            // drawableLeft <-> drawableStart
            "drawableLeft",             "drawableStart",
            // drawableRight <-> drawableEnd
            "drawableRight",            "drawableEnd",
    };

    /**
     * Returns true if the given attribute name is an RTL-specific attribute (uses Start/End).
     */
    public static boolean isRtlAttributeName(@NonNull String attribute) {
        // Strip layout_ prefix for comparison
        String name = attribute;
        if (name.startsWith("layout_")) {
            name = name.substring("layout_".length());
        }
        return name.endsWith("Start") || name.endsWith("End");
    }

    /**
     * Converts an old left/right attribute name to the new start/end equivalent.
     * Returns null if no conversion is available.
     */
    public static String convertOldToNew(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        // Generic conversion: replace Left->Start, Right->End
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return null;
    }

    /**
     * Converts a new start/end attribute name to the old left/right equivalent.
     * Returns null if no conversion is available.
     */
    public static String convertNewToOld(@NonNull String attribute) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        // Generic conversion: replace Start->Left, End->Right
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - "Start".length()) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - "End".length()) + "Right";
        }
        return null;
    }

    /**
     * Converts an attribute to its opposite direction counterpart.
     * Left <-> Right, Start <-> End.
     */
    public static String convertToOppositeDirection(@NonNull String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - "Start".length()) + "End";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - "End".length()) + "Start";
        }
        return attribute;
    }

    /**
     * Returns the API version encoded in a folder name (e.g. "layout-v17" returns 17),
     * or -1 if no version qualifier is found.
     */
    public static int getFolderVersion(@NonNull File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index == -1) {
            // Also check for just the folder name itself if it IS the file
            // Try parent
            return -1;
        }
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
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about android:textAlignment
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            return;
        }

        // Check if the project's minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            // No compatibility issue needed
            return;
        }

        // Check whether the element also has a gravity or layout_gravity attribute
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        boolean hasLayoutGravity = false;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (ANDROID_URI.equals(attr.getNamespaceURI())) {
                String localName = attr.getLocalName();
                if (ATTR_GRAVITY.equals(localName)) {
                    hasGravity = true;
                } else if (ATTR_LAYOUT_GRAVITY.equals(localName)) {
                    hasLayoutGravity = true;
                }
            }
        }

        if (!hasGravity && !hasLayoutGravity) {
            String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) " +
                    "you should also specify `gravity` or `layout_gravity` when using " +
                    "`textAlignment` attribute",
                    minSdk
            );
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}