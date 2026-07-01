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
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String[] ATTRIBUTES = {
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "alignParentLeft", "alignParentStart",
        "alignParentRight", "alignParentEnd",
        "alignLeft", "alignStart",
        "alignRight", "alignEnd",
        "toLeftOf", "toStartOf",
        "toRightOf", "toEndOf"
    };

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (!hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, context.getLocation(attribute),
                "Consider adding `android:gravity` or `android:layout_gravity` for compatibility with API < 17");
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        }
        if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        }
        if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        }
        if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        }
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int dash = name.indexOf('-');
        if (dash != -1) {
            String qualifiers = name.substring(dash + 1);
            int vIndex = qualifiers.indexOf('v');
            if (vIndex != -1) {
                try {
                    return Integer.parseInt(qualifiers.substring(vIndex + 1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return 1;
    }
}