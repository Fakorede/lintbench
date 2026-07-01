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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final String PNG_EXTENSION = ".png";
    private static final String NINE_PATCH_EXTENSION = ".9.png";

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        String name = file.getName();

        if (endsWithIgnoreCase(name, NINE_PATCH_EXTENSION)) {
            return;
        }

        if (!endsWithIgnoreCase(name, PNG_EXTENSION)) {
            return;
        }

        String base = name.substring(0, name.length() - PNG_EXTENSION.length());
        File ninePatchFile = new File(folder, base + NINE_PATCH_EXTENSION);
        if (!ninePatchFile.exists()) {
            return;
        }

        Location location = Location.create(file);
        Location secondary = Location.create(ninePatchFile);
        location.setSecondary(secondary);

        String message = String.format(
                "Found both `%1$s.png` and `%1$s.9.png` in the same drawable folder; " +
                "both map to @drawable/%1$s",
                base);

        context.report(ISSUE_MIXED_NINE_PATCH, location, message);
    }

    private static boolean endsWithIgnoreCase(@NonNull String string, @NonNull String suffix) {
        return string.length() >= suffix.length()
                && string.regionMatches(true, string.length() - suffix.length(), suffix, 0, suffix.length());
    }
}