package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.ResourceFolderDetector {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.INFORMATIONAL,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String EXT_PNG = "png";
    private static final String EXT_JPG = "jpg";
    private static final String EXT_JPEG = "jpeg";
    private static final String NINE_PATCH = ".9.png";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkResourceFolder(
            @NonNull ResourceContext context,
            @NonNull ResourceFolderType folderType,
            @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String name = file.getName();
            String lower = name.toLowerCase(Locale.US);

            if (lower.endsWith(NINE_PATCH)) {
                continue;
            }

            if (lower.endsWith("." + EXT_PNG)
                    || lower.endsWith("." + EXT_JPG)
                    || lower.endsWith("." + EXT_JPEG)) {
                String message = String.format(
                        Locale.US,
                        "This image could be converted to WebP: %1$s",
                        name);
                context.report(CONVERT_TO_WEBP, Location.create(file), message);
            }
        }
    }
}