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
import java.util.Arrays;
import java.util.Collection;

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
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
            "layout_alignParentLeft", "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart",
            "layout_alignRight", "layout_alignEnd",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
            "layout_toLeftOf", "layout_toStartOf",
            "layout_toRightOf", "layout_toEndOf"
    };

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            "textAlignment",
            "layout_alignParentLeft", "layout_alignParentStart", "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart", "layout_alignRight", "layout_alignEnd",
            "layout_marginLeft", "layout_marginStart", "layout_marginRight", "layout_marginEnd",
            "paddingLeft", "paddingStart", "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart", "drawableRight", "drawableEnd",
            "layout_toLeftOf", "layout_toStartOf", "layout_toRightOf", "layout_toEndOf"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        int minSdk = context.getProject().getMinSdk();
        int targetSdk = context.getProject().getTargetSdk();

        if (name.equals("textAlignment")) {
            if (minSdk < 17) {
                String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "gravity");
                String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
                if (gravity.isEmpty() && layoutGravity.isEmpty()) {
                    String message = String.format(
                        "Attribute `%1$s` is only used in API level 17 and higher " +
                        "(current min is %2$d); you should also specify `android:gravity` or `android:layout_gravity`",
                        name, minSdk);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
            return;
        }

        boolean isStartOrEnd = name.endsWith("Start") || name.endsWith("End");
        boolean isLeftOrRight = name.endsWith("Left") || name.endsWith("Right");

        if (isStartOrEnd && minSdk < 17) {
            String counterpart = convertNewToOld(name);
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
                String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) you should **also** specify attribute `%2$s`",
                    minSdk, "android:" + counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (isLeftOrRight && targetSdk >= 17) {
            String counterpart = convertOldToNew(name);
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
                String message = String.format(
                    "To support RTL from API 17 and higher (project targets %1$d) you should **also** specify attribute `%2$s`",
                    targetSdk, "android:" + counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    public static boolean isRtlAttributeName(String attribute) {
        return attribute.endsWith("Start") || attribute.endsWith("End");
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Start";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Right";
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Right";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Start";
        }
        return attribute;
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
        return -1;
    }
}