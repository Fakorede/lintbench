package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `textAlignment` attribute was introduced in API 17. When supporting older "
                    + "versions, you must also specify a `gravity` or `layout_gravity` "
                    + "attribute so the alignment is honored on pre-API 17 devices.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        File parent = context.file.getParentFile();
        if (parent != null && isVersionFolderAtLeast(parent.getName(), 17)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, SdkConstants.ATTR_GRAVITY)
                || hasAndroidAttribute(element, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "To support older versions than API 17, you must also specify `gravity` or `layout_gravity`"
        );
    }

    private static boolean hasAndroidAttribute(@NonNull Element element, @NonNull String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }

    private static boolean isVersionFolderAtLeast(@NonNull String folderName, int minApi) {
        int index = folderName.indexOf("-v");
        while (index != -1) {
            int start = index + 2;
            int end = start;
            while (end < folderName.length() && Character.isDigit(folderName.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    int version = Integer.parseInt(folderName.substring(start, end));
                    if (version >= minApi) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            index = folderName.indexOf("-v", index + 1);
        }
        return false;
    }
}