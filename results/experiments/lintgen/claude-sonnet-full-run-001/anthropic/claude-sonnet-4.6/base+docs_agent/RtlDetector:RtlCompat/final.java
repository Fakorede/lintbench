package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Arrays;
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
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - "Start".length()) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - "End".length()) + "Right";
        }
        return null;
    }

    /**
     * Converts an attribute to its opposite direction counterpart.
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
            return -1;
        }
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
        if (versionStr.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(versionStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        // We need to check textAlignment as well as all the left/right/start/end attributes
        return Arrays.asList(
                ATTR_TEXT_ALIGNMENT,
                // left/right attributes
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_alignParentLeft",
                "layout_alignParentRight",
                "layout_alignLeft",
                "layout_alignRight",
                "layout_toLeftOf",
                "layout_toRightOf",
                "drawableLeft",
                "drawableRight",
                // start/end attributes
                "paddingStart",
                "paddingEnd",
                "layout_marginStart",
                "layout_marginEnd",
                "layout_alignParentStart",
                "layout_alignParentEnd",
                "layout_alignStart",
                "layout_alignEnd",
                "layout_toStartOf",
                "layout_toEndOf",
                "drawableStart",
                "drawableEnd"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String localName = attribute.getLocalName();

        // Check if the project's minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();

        if (ATTR_TEXT_ALIGNMENT.equals(localName)) {
            // textAlignment requires API 17; if minSdk < 17, must also have gravity/layout_gravity
            if (minSdk >= RTL_API) {
                return;
            }

            // Check folder version qualifier - if the layout is in a -v17 or higher folder,
            // no issue
            File folder = context.file.getParentFile();
            if (folder != null) {
                int folderVersion = getFolderVersion(folder);
                if (folderVersion >= RTL_API) {
                    return;
                }
            }

            Element element = attribute.getOwnerElement();
            NamedNodeMap attributes = element.getAttributes();

            boolean hasGravity = false;
            boolean hasLayoutGravity = false;

            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                if (ANDROID_URI.equals(attr.getNamespaceURI())) {
                    String attrName = attr.getLocalName();
                    if (ATTR_GRAVITY.equals(attrName)) {
                        hasGravity = true;
                    } else if (ATTR_LAYOUT_GRAVITY.equals(attrName)) {
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
        } else {
            // For left/right/start/end attributes, check compatibility
            // If minSdk >= 17, we only need start/end attributes
            // If minSdk < 17, we need both old (left/right) and new (start/end) attributes

            boolean isRtl = isRtlAttributeName(localName);

            if (isRtl) {
                // This is a start/end attribute
                // If minSdk < 17, check that the corresponding left/right attribute also exists
                if (minSdk < RTL_API) {
                    // Check folder version - if folder is v17+, no need for old attributes
                    File folder = context.file.getParentFile();
                    if (folder != null) {
                        int folderVersion = getFolderVersion(folder);
                        if (folderVersion >= RTL_API) {
                            return;
                        }
                    }

                    String oldAttr = convertNewToOld(localName);
                    if (oldAttr != null) {
                        Element element = attribute.getOwnerElement();
                        if (element.getAttributeNodeNS(ANDROID_URI, oldAttr) == null) {
                            String message = String.format(
                                    "To support older versions than API 17 (project specifies %1$d) " +
                                    "you should also specify `%2$s`",
                                    minSdk, oldAttr
                            );
                            context.report(ISSUE, attribute, context.getLocation(attribute), message);
                        }
                    }
                }
            } else {
                // This is a left/right attribute
                // If minSdk < 17 but targetSdk >= 17, check that the corresponding start/end
                // attribute also exists
                int targetSdk = context.getMainProject().getTargetSdk();
                if (targetSdk >= RTL_API) {
                    // Check folder version
                    File folder = context.file.getParentFile();
                    if (folder != null) {
                        int folderVersion = getFolderVersion(folder);
                        if (folderVersion >= RTL_API) {
                            return;
                        }
                    }

                    String newAttr = convertOldToNew(localName);
                    if (newAttr != null) {
                        Element element = attribute.getOwnerElement();
                        if (element.getAttributeNodeNS(ANDROID_URI, newAttr) == null) {
                            String message = String.format(
                                    "Consider adding `%1$s` since `targetSdkVersion` is %2$d",
                                    newAttr, targetSdk
                            );
                            context.report(ISSUE, attribute, context.getLocation(attribute), message);
                        }
                    }
                }
            }
        }
    }
}