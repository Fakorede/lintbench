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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

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
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public static final String[] ATTRIBUTES = new String[] {
            "layout_alignParentLeft",   "layout_alignParentStart",
            "layout_alignParentRight",  "layout_alignParentEnd",
            "layout_alignLeft",         "layout_alignStart",
            "layout_alignRight",        "layout_alignEnd",
            "layout_marginLeft",        "layout_marginStart",
            "layout_marginRight",       "layout_marginEnd",
            "layout_toLeftOf",          "layout_toStartOf",
            "layout_toRightOf",         "layout_toEndOf",
            "paddingLeft",              "paddingStart",
            "paddingRight",             "paddingEnd",
            "drawableLeft",             "drawableStart",
            "drawableRight",            "drawableEnd",
    };

    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    public RtlDetector() {
    }

    /**
     * Returns the API version encoded in the folder name, or -1 if no version is encoded.
     * For example, for a folder named "layout-v17", this returns 17.
     */
    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index == -1) {
            return -1;
        }
        String versionString = name.substring(index + 2);
        // Handle cases where there might be additional qualifiers after the version
        int end = versionString.length();
        for (int i = 0; i < versionString.length(); i++) {
            char c = versionString.charAt(i);
            if (!Character.isDigit(c)) {
                end = i;
                break;
            }
        }
        versionString = versionString.substring(0, end);
        if (versionString.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(versionString);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isRtlAttributeName(String name) {
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return null;
    }

    public static String convertNewToOld(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        return null;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - "Start".length()) + "End";
        }
        if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - "End".length()) + "Start";
        }
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - "Left".length()) + "Right";
        }
        if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - "Right".length()) + "Left";
        }
        return attribute;
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
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
                    "To support older versions than API 17 (project specifies " + minSdk + ") " +
                    "you must also specify `gravity` or `layout_gravity` when specifying " +
                    "`textAlignment`"
            );
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Not used
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Not used
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        // Not used
    }
}