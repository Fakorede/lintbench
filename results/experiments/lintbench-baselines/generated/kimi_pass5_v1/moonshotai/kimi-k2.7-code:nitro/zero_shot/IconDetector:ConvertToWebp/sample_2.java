package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

public class IconDetector extends Detector implements ResourceFolderScanner {

    private static final String PNG = ".png";
    private static final String JPG = ".jpg";
    private static final String JPEG = ".jpeg";
    private static final String NINE_PATCH = ".9.png";

    public static final Issue WEBP_ELIGIBLE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion. Previously, launcher "
                    + "icons were required to be in the PNG format but that restriction is no longer "
                    + "there, so lint now flags these.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
    }

    @Override
    public void checkFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        if (endsWithIgnoreCase(name, NINE_PATCH)) {
            return;
        }

        if (endsWithIgnoreCase(name, PNG)
                || endsWithIgnoreCase(name, JPG)
                || endsWithIgnoreCase(name, JPEG)) {
            Location location = Location.create(file);
            context.report(WEBP_ELIGIBLE, location,
                    "This image could be converted to WebP for a smaller file size.");
        }
    }

    private static boolean endsWithIgnoreCase(@NonNull String string, @NonNull String suffix) {
        int suffixLength = suffix.length();
        if (suffixLength > string.length()) {
            return false;
        }
        return string.regionMatches(true, string.length() - suffixLength, suffix, 0, suffixLength);
    }
}