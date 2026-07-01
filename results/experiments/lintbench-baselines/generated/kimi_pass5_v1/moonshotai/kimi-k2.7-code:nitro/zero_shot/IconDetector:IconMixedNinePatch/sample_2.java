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
import java.util.HashSet;
import java.util.Set;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            3,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Set<String> pngNames = new HashSet<String>();
        Set<String> ninePatchNames = new HashSet<String>();

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String resourceName = name.substring(0, name.length() - ".9.png".length());
                ninePatchNames.add(resourceName);
                if (pngNames.contains(resourceName)) {
                    reportClash(context, file, resourceName);
                }
            } else if (name.endsWith(".png")) {
                String resourceName = name.substring(0, name.length() - ".png".length());
                pngNames.add(resourceName);
                if (ninePatchNames.contains(resourceName)) {
                    reportClash(context, file, resourceName);
                }
            }
        }
    }

    private static void reportClash(@NonNull Context context, @NonNull File file,
            @NonNull String resourceName) {
        String message = String.format(
                "Found both `%s.png` and `%s.9.png` resources which both map to `@drawable/%s`",
                resourceName, resourceName, resourceName);
        context.report(ISSUE, Location.create(file), message);
    }
}