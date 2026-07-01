package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements Detector.FileScanner {

    public static final Issue ICON_MIXED_NINE_PATCH = Issue.create(
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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    /** Map from base name (e.g. "file") to list of files that map to it */
    private final Map<String, List<File>> mFileMap = new HashMap<>();

    public IconDetector() {
    }

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.ALL_RESOURCE_FILES);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileMap.clear();
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();

        // Only interested in PNG files in drawable directories
        if (!name.endsWith(".png")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        if (!parentName.startsWith("drawable")) {
            return;
        }

        // Compute the base resource name
        String baseName = getBaseName(name);
        if (baseName == null) {
            return;
        }

        // Use parent path + baseName as key to group files within the same drawable folder
        String key = parent.getPath() + "/" + baseName;

        List<File> files = mFileMap.get(key);
        if (files == null) {
            files = new ArrayList<>();
            mFileMap.put(key, files);
        }
        files.add(file);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<File>> entry : mFileMap.entrySet()) {
            List<File> files = entry.getValue();
            if (files.size() < 2) {
                continue;
            }

            // Check if we have both a .png and a .9.png
            boolean hasRegularPng = false;
            boolean hasNinePatch = false;
            File regularFile = null;
            File ninePatchFile = null;

            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".9.png")) {
                    hasNinePatch = true;
                    ninePatchFile = file;
                } else if (name.endsWith(".png")) {
                    hasRegularPng = true;
                    regularFile = file;
                }
            }

            if (hasRegularPng && hasNinePatch && regularFile != null && ninePatchFile != null) {
                // Determine the base name for the message
                String baseName = getBaseName(regularFile.getName());
                if (baseName == null) {
                    baseName = regularFile.getName();
                }

                String message = String.format(
                        "The files `%1$s` and `%2$s` both map to the same drawable resource " +
                        "name `@drawable/%3$s`; check that this is intentional",
                        regularFile.getName(),
                        ninePatchFile.getName(),
                        baseName
                );

                Location location = Location.create(regularFile);
                Location secondary = Location.create(ninePatchFile);
                secondary.setMessage("Also maps to @drawable/" + baseName);
                location.setSecondary(secondary);

                context.report(ICON_MIXED_NINE_PATCH, location, message);
            }
        }
    }

    /**
     * Returns the base resource name for a PNG file.
     * For "file.9.png" returns "file".
     * For "file.png" returns "file".
     * Returns null if the file is not a PNG.
     */
    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }
}