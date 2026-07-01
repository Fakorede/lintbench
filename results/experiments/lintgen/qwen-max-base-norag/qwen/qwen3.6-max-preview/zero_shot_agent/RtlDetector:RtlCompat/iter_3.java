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
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {

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
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String[] ATTRIBUTES = {
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "toLeftOf", "toStartOf",
            "toRightOf", "toEndOf",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "marginLeft", "marginStart",
            "marginRight", "marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
            "gravity", "textAlignment"
    };

    public static boolean isRtlAttributeName(String name) {
        String base = name.startsWith("layout_") ? name.substring(7) : name;
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(base)) {
                return true;
            }
        }
        return false;
    }

    public static String convertOldToNew(String oldName) {
        String prefix = "";
        String base = oldName;
        if (oldName.startsWith("layout_")) {
            prefix = "layout_";
            base = oldName.substring(7);
        }
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(base)) {
                return prefix + ATTRIBUTES[i + 1];
            }
        }
        return oldName;
    }

    public static String convertNewToOld(String newName) {
        String prefix = "";
        String base = newName;
        if (newName.startsWith("layout_")) {
            prefix = "layout_";
            base = newName.substring(7);
        }
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(base)) {
                return prefix + ATTRIBUTES[i - 1];
            }
        }
        return newName;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.contains("Left")) {
            return name.replace("Left", "Right");
        }
        if (name.contains("Right")) {
            return name.replace("Right", "Left");
        }
        if (name.contains("Start")) {
            return name.replace("Start", "End");
        }
        if (name.contains("End")) {
            return name.replace("End", "Start");
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 1;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        int minSdk = context.getMainProject().getMinSdkVersion() != null
                ? context.getMainProject().getMinSdkVersion().getApiLevel()
                : 1;
        if (minSdk >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (hasGravity || hasLayoutGravity) {
            return;
        }

        String message = "To support older versions than API 17 (project specifies " + minSdk +
                ") you should **also** specify `android:gravity` or `android:layout_gravity`";
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}