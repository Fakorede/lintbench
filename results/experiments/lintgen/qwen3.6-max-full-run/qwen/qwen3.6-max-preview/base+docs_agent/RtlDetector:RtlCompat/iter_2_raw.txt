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
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends Detector implements XmlScanner {

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
        "toRightOf", "toEndOf"
    };

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
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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
        String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (gravity.isEmpty() && layoutGravity.isEmpty()) {
            String message = String.format(
                    "Attribute `%1$s` is only used in API level 17 and higher " +
                    "(current min is %2$d); you should also specify `android:gravity` or `android:layout_gravity`",
                    SdkConstants.ATTR_TEXT_ALIGNMENT, minSdk);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    public static String convertOldToNew(String name) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i - 1];
            }
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.indexOf("Left") != -1) {
            return name.replace("Left", "Right");
        }
        if (name.indexOf("Right") != -1) {
            return name.replace("Right", "Left");
        }
        if (name.indexOf("Start") != -1) {
            return name.replace("Start", "End");
        }
        if (name.indexOf("End") != -1) {
            return name.replace("End", "Start");
        }
        return name;
    }

    public static boolean isRtlAttributeName(String name) {
        if (name == null) return false;
        String base = name;
        if (base.startsWith("android:")) {
            base = base.substring(8);
        }
        if (base.startsWith("layout_")) {
            base = base.substring(7);
        }
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(base)) {
                return true;
            }
        }
        return false;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.indexOf('-');
        if (index != -1) {
            String qualifier = name.substring(index + 1);
            if (qualifier.startsWith("v")) {
                try {
                    return Integer.parseInt(qualifier.substring(1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return 1;
    }
}