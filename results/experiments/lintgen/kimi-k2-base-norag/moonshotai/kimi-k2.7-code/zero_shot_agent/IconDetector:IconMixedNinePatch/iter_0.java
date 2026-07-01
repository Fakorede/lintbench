package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceFolderDetector {

    private static final String PNG_EXTENSION = ".png";
    private static final String NINE_PATCH_EXTENSION = ".9.png";

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE);

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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> seen = new HashMap<>();

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            boolean isNinePatch = endsWithIgnoreCase(name, NINE_PATCH_EXTENSION);
            boolean isPng = !isNinePatch && endsWithIgnoreCase(name, PNG_EXTENSION);

            if (!isNinePatch && !isPng) {
                continue;
            }

            int extensionLength = isNinePatch ? NINE_PATCH_EXTENSION.length() : PNG_EXTENSION.length();
            String base = name.substring(0, name.length() - extensionLength);

            File previous = seen.get(base);
            if (previous == null) {
                seen.put(base, file);
                continue;
            }

            boolean previousIsNinePatch = endsWithIgnoreCase(previous.getName(), NINE_PATCH_EXTENSION);
            if (previousIsNinePatch != isNinePatch) {
                Location location = Location.create(file);
                Location other = Location.create(previous);
                location.setSecondary(other);

                String message = String.format(
                        "Found both `%1$s.png` and `%1$s.9.png` in the same drawable folder; " +
                        "both map to @drawable/%1$s",
                        base);

                context.report(ISSUE_MIXED_NINE_PATCH, location, message);
            }
        }
    }

    private static boolean endsWithIgnoreCase(@NonNull String string, @NonNull String suffix) {
        return string.length() >= suffix.length()
                && string.regionMatches(true, string.length() - suffix.length(), suffix, 0, suffix.length());
    }
}