package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue COMPAT = Issue.create(
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

    public static final Issue ENABLED = Issue.create(
            "RtlEnabled",
            "Using RTL attributes without enabling RTL support",
            "To support right-to-left layouts, you must specify `android:supportsRtl=\"true\"` " +
            "in the manifest.",
            Category.RTL,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue HARDCODED = Issue.create(
            "RtlHardcoded",
            "Using Left/Right instead of Start/End attributes",
            "Using Left/Right instead of Start/End attributes",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue ISSUE = COMPAT;

    public static final String[] ATTRIBUTES = new String[] {
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd"
    };

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int index = name.indexOf("-v");
        while (index != -1) {
            int start = index + 2;
            int end = start;
            while (end < name.length() && Character.isDigit(name.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    return Integer.parseInt(name.substring(start, end));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            index = name.indexOf("-v", index + 1);
        }
        return -1;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> attributes = new ArrayList<>();
        attributes.add(SdkConstants.ATTR_TEXT_ALIGNMENT);
        for (String attr : ATTRIBUTES) {
            attributes.add(attr);
        }
        return attributes;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            int minSdk = context.getProject().getMinSdk();
            int folderVersion = getFolderVersion(context.file);
            if (folderVersion >= 17) {
                return;
            }
            if (minSdk >= 17) {
                return;
            }

            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY) &&
                    !element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
                context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify a gravity or layout_gravity attribute"
                );
            }
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        int folderVersion = getFolderVersion(context.file);
        if (folderVersion >= 17) {
            return;
        }

        boolean isStartEnd = isRtlAttributeName(name);

        if (isStartEnd) {
            if (minSdk < 17) {
                String oldAttr = convertNewToOld(name);
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, oldAttr)) {
                    context.report(
                            COMPAT,
                            attribute,
                            context.getLocation(attribute),
                            String.format("To support older versions than API 17, you must also specify `%s`", oldAttr)
                    );
                }
            }

            int targetSdk = context.getProject().getTargetSdk();
            if (targetSdk >= 17) {
                Boolean supportRtl = context.getProject().getSupportRtl();
                if (supportRtl == null || !supportRtl) {
                    context.report(
                            ENABLED,
                            attribute,
                            context.getLocation(attribute),
                            "To support right-to-left layouts, you must specify `android:supportsRtl=\"true\"` in the manifest"
                    );
                }
            }
        } else {
            if (minSdk >= 17) {
                String newAttr = convertOldToNew(name);
                context.report(
                        HARDCODED,
                        attribute,
                        context.getLocation(attribute),
                        String.format("Use `%s` instead of `%s` to ensure correct behavior on right-to-left layouts", newAttr, name)
                );
            } else {
                int targetSdk = context.getProject().getTargetSdk();
                if (targetSdk >= 17) {
                    String newAttr = convertOldToNew(name);
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, newAttr)) {
                        context.report(
                                COMPAT,
                                attribute,
                                context.getLocation(attribute),
                                String.format("To support RTL, you should also specify `%s`", newAttr)
                        );
                    }
                }
            }
        }
    }
}