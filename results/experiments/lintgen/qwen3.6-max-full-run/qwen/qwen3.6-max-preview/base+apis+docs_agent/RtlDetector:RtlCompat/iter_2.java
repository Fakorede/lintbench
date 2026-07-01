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
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must also specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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
            "left", "start",
            "right", "end"
    };

    public static String convertOldToNew(String name) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i + 1].equals(name)) {
                return ATTRIBUTES[i];
            }
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.contains("Left")) {
            return name.replace("Left", "Right");
        } else if (name.contains("Right")) {
            return name.replace("Right", "Left");
        } else if (name.contains("Start")) {
            return name.replace("Start", "End");
        } else if (name.contains("End")) {
            return name.replace("End", "Start");
        }
        return name;
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int index = name.indexOf("-v");
        if (index == -1 && file.getParentFile() != null) {
            name = file.getParentFile().getName();
            index = name.indexOf("-v");
        }
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
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }
        Element element = attribute.getOwnerElement();
        String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "gravity");
        String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");

        if (gravity.isEmpty() && layoutGravity.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When using `textAlignment`, also specify `android:gravity` or `android:layout_gravity` for compatibility with API < 17");
        }
    }
}