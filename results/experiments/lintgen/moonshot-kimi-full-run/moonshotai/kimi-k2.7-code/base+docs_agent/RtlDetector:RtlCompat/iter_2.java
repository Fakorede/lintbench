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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RtlDetector extends LayoutDetector {

    private static final Map<String, String> NEW_TO_OLD = new HashMap<>();
    private static final Map<String, String> OLD_TO_NEW = new HashMap<>();

    private static final String[] ATTRIBUTES = new String[] {
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
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, if you "
                    + "are supporting older versions than API 17, you must **also** specify a "
                    + "gravity or layout_gravity attribute, since older platforms will ignore the "
                    + "`textAlignment` attribute.\n"
                    + "\n"
                    + "Similarly, API 17 adds `start` and `end` versions of many attributes which "
                    + "mirror the `left` and `right` attributes. On older platforms these are "
                    + "ignored, so you must specify both the `left`/`right` attribute as well as "
                    + "the `start`/`end` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        Collection<String> attributes = new ArrayList<>(NEW_TO_OLD.size() + 1);
        attributes.addAll(NEW_TO_OLD.keySet());
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
            return;
        }

        String oldAttribute = NEW_TO_OLD.get(name);
        if (oldAttribute != null && !hasAndroidAttribute(element, oldAttribute)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "To support older versions than API 17, you must also specify a "
                                    + "`%1$s` attribute",
                            oldAttribute
                    )
            );
        }
    }

    @Nullable
    public static String convertNewToOld(@NonNull String attribute) {
        return NEW_TO_OLD.get(attribute);
    }

    @Nullable
    public static String convertOldToNew(@NonNull String attribute) {
        return OLD_TO_NEW.get(attribute);
    }

    private static int getFolderVersion(@NonNull File file) {
        File current = file.getParentFile();
        while (current != null) {
            int version = getVersionFromFolderName(current.getName());
            if (version != -1) {
                return version;
            }
            current = current.getParentFile();
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