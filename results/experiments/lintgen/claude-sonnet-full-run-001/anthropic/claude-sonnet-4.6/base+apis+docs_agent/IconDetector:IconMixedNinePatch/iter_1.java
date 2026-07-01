package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE_ICON_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        // Map from base name (the resource name, e.g. "file" for both "file.png" and "file.9.png")
        // to the list of actual files that map to that name
        Map<String, List<File>> nameToFiles = new HashMap<>();

        for (File file : files) {
            String name = file.getName();
            String baseName = getResourceBaseName(name);
            if (baseName == null) {
                continue;
            }

            List<File> fileList = nameToFiles.get(baseName);
            if (fileList == null) {
                fileList = new ArrayList<>();
                nameToFiles.put(baseName, fileList);
            }
            fileList.add(file);
        }

        // Now check for conflicts: same base name but one is a .9.png and another is a .png
        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> fileList = entry.getValue();
            if (fileList.size() < 2) {
                continue;
            }

            boolean hasNinePatch = false;
            boolean hasRegularPng = false;
            File ninePatchFile = null;
            File regularPngFile = null;

            for (File file : fileList) {
                String fileName = file.getName();
                if (fileName.endsWith(".9.png")) {
                    hasNinePatch = true;
                    ninePatchFile = file;
                } else if (fileName.endsWith(".png")) {
                    hasRegularPng = true;
                    regularPngFile = file;
                }
            }

            if (hasNinePatch && hasRegularPng) {
                String baseName = entry.getKey();
                String message = String.format(
                        "The files `%1$s.png` and `%1$s.9.png` clash; both will map to `@drawable/%1$s`",
                        baseName);

                Location location = Location.create(ninePatchFile);
                if (regularPngFile != null) {
                    Location secondary = Location.create(regularPngFile);
                    secondary.setMessage("Also maps to @drawable/" + baseName);
                    location.setSecondary(secondary);
                }

                context.report(ISSUE_ICON_MIXED_NINE_PATCH, location, message);
            }
        }
    }

    /**
     * Returns the resource base name for a given file name, or null if the file
     * is not a PNG or 9-patch file.
     *
     * For "file.png" returns "file".
     * For "file.9.png" returns "file".
     */
    private static String getResourceBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }
}