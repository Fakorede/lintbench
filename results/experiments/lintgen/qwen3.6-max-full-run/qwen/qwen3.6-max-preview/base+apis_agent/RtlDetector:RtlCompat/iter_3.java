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

    private static final int RTL_API = 17;

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
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd"
    };

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int vIndex = name.lastIndexOf("-v");
        if (vIndex != -1) {
            try {
                return Integer.parseInt(name.substring(vIndex + 2));
            } catch (NumberFormatException ignored) {
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
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < RTL_API) {
                Element element = attribute.getOwnerElement();
                boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "gravity");
                boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
                if (!hasGravity && !hasLayoutGravity) {
                    context.report(ISSUE, context.getLocation(attribute),
                        "Consider adding android:gravity or android:layout_gravity for compatibility with API < 17");
                }
            }
        }
    }
}