package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    // Map from resource folder path -> map from base name (without extension) -> list of files
    private final Map<String, Map<String, List<File>>> mFolderToNameMap = new HashMap<>();

    public IconDetector() {
    }

    @Override
    public void checkResourceFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        // Map from base name (the resource name, e.g. "file" for both "file.png" and "file.9.png")
        // to the list of actual files with that base name
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

        // Check for clashes: same base name but one is a .9.png and another is a .png
        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> fileList = entry.getValue();
            if (fileList.size() < 2) {
                continue;
            }

            boolean hasNinePatch = false;
            boolean hasPng = false;
            File ninePatchFile = null;
            File pngFile = null;

            for (File file : fileList) {
                String name = file.getName();
                if (name.endsWith(".9.png")) {
                    hasNinePatch = true;
                    ninePatchFile = file;
                } else if (name.endsWith(".png")) {
                    hasPng = true;
                    pngFile = file;
                }
            }

            if (hasNinePatch && hasPng) {
                String baseName = entry.getKey();
                Location location = Location.create(ninePatchFile);
                Location secondary = Location.create(pngFile);
                secondary.setMessage("This file clashes with the nine-patch file");
                location.setSecondary(secondary);

                context.report(
                        ISSUE_ICON_MIXED_NINE_PATCH,
                        location,
                        String.format(
                                "The files `%1$s.png` and `%1$s.9.png` both map to " +
                                "the same resource id `@drawable/%1$s`; rename or delete one of them",
                                baseName
                        )
                );
            }
        }
    }

    /**
     * Returns the resource base name for a given file name, or null if the file
     * is not a PNG or 9-patch PNG.
     *
     * For "file.9.png" returns "file"
     * For "file.png" returns "file"
     * For other files returns null
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