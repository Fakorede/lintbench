package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RtlDetector extends LayoutDetector {

    private static final Map<String, String> OLD_TO_NEW = new HashMap<>();
    private static final Map<String, String> NEW_TO_OLD = new HashMap<>();

    public static final String[] ATTRIBUTES = new String[] {
            SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
            SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
            SdkConstants.ATTR_LAYOUT_ALIGN_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_START,
            SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_END,
            SdkConstants.ATTR_LAYOUT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_TO_START_OF,
            SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_TO_END_OF,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_END,
            SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_START,
            SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING_END,
            SdkConstants.ATTR_DRAWABLE_LEFT, SdkConstants.ATTR_DRAWABLE_START,
            SdkConstants.ATTR_DRAWABLE_RIGHT, SdkConstants.ATTR_DRAWABLE_END
    };

    static {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String oldAttr = ATTRIBUTES[i];
            String newAttr = ATTRIBUTES[i + 1];
            OLD_TO_NEW.put(oldAttr, newAttr);
            NEW_TO_OLD.put(newAttr, oldAttr);
        }
    }

    private static final Implementation IMPLEMENTATION = new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `textAlignment` attribute and other RTL attributes were introduced in API 17. "
                    + "When supporting older versions, you must also specify the older gravity "
                    + "or left/right counterparts so the layout is honored on pre-API 17 devices.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> attributes = new ArrayList<>(ATTRIBUTES.length + 1);
        Collections.addAll(attributes, ATTRIBUTES);
        attributes.add(SdkConstants.ATTR_TEXT_ALIGNMENT);
        return attributes;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        if (getFolderVersion(context.file) >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            if (!hasAndroidAttribute(element, SdkConstants.ATTR_GRAVITY)
                    && !hasAndroidAttribute(element, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify a "
                                + "`gravity` or `layout_gravity` attribute"
                );
            }
        } else if (isRtlAttributeName(name)) {
            String oldAttribute = convertNewToOld(name);
            if (oldAttribute != null
                    && !hasAndroidAttribute(element, oldAttribute)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify a "
                                + "`" + oldAttribute + "` attribute"
                );
            }
        }
    }

    public static boolean isRtlAttributeName(@NonNull String name) {
        return name.endsWith("Start") || name.endsWith("End")
                || name.equals(SdkConstants.ATTR_TEXT_ALIGNMENT)
                || name.equals(SdkConstants.ATTR_LAYOUT_DIRECTION);
    }

    @Nullable
    public static String convertOldToNew(@NonNull String attribute) {
        return OLD_TO_NEW.get(attribute);
    }

    @Nullable
    public static String convertNewToOld(@NonNull String attribute) {
        return NEW_TO_OLD.get(attribute);
    }

    @NonNull
    public static String convertToOppositeDirection(@NonNull String attribute) {
        if (attribute.contains("Start") || attribute.contains("End")) {
            String temp = attribute.replace("Start", "\u0000");
            temp = temp.replace("End", "Start");
            return temp.replace("\u0000", "End");
        } else if (attribute.contains("Left") || attribute.contains("Right")) {
            String temp = attribute.replace("Left", "\u0000");
            temp = temp.replace("Right", "Left");
            return temp.replace("\u0000", "Right");
        }
        return attribute;
    }

    public static int getFolderVersion(@NonNull File file) {
        int version = getVersionFromFolderName(file.getName());
        if (version != -1) {
            return version;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            version = getVersionFromFolderName(parent.getName());
            if (version != -1) {
                return version;
            }
        }
        return -1;
    }

    private static int getVersionFromFolderName(@NonNull String name) {
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
                } catch (NumberFormatException ignored) {
                }
            }
            index = name.indexOf("-v", index + 1);
        }
        return -1;
    }

    private static boolean hasAndroidAttribute(@NonNull Element element, @NonNull String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }
}