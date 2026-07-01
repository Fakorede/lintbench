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

    public static final String[] ATTRIBUTES = {
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "toLeftOf", "toStartOf",
            "toRightOf", "toEndOf",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
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
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject().getMinSdkVersion().getApiLevel() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (!hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When using `textAlignment` on older platforms, also specify `gravity` or `layout_gravity` for compatibility");
        }
    }

    public static boolean isRtlAttributeName(String name) {
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return true;
            }
        }
        return false;
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
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 4) + "Left";
        } else if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int index = name.indexOf('-');
        if (index != -1) {
            int vIndex = name.indexOf('v', index);
            if (vIndex != -1) {
                try {
                    return Integer.parseInt(name.substring(vIndex + 1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return 1;
    }
}