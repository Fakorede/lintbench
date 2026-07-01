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

    public static final Issue ICON_MIX_9PNG = Issue.create(
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

    // Map from resource folder (e.g. "res/drawable-hdpi") to a map of
    // base name (e.g. "foo") to list of files with that base name.
    private final Map<File, Map<String, List<File>>> mFolderToBaseNameMap = new HashMap<>();

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderToBaseNameMap.clear();
    }

    @Override
    public void run(@NonNull Context context) {
        // This detector works by collecting files during checkFile and then
        // reporting in afterCheckRootProject.
        File file = context.file;
        String name = file.getName();

        // Only interested in PNG files
        if (!name.endsWith(".png")) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        // Only look in drawable folders
        String folderName = folder.getName();
        if (!folderName.startsWith("drawable")) {
            return;
        }

        // Determine the base name:
        // For "foo.9.png" -> base name is "foo"
        // For "foo.png"   -> base name is "foo"
        String baseName;
        if (name.endsWith(".9.png")) {
            baseName = name.substring(0, name.length() - ".9.png".length());
        } else {
            baseName = name.substring(0, name.length() - ".png".length());
        }

        Map<String, List<File>> baseNameMap = mFolderToBaseNameMap.get(folder);
        if (baseNameMap == null) {
            baseNameMap = new HashMap<>();
            mFolderToBaseNameMap.put(folder, baseNameMap);
        }

        List<File> files = baseNameMap.get(baseName);
        if (files == null) {
            files = new ArrayList<>();
            baseNameMap.put(baseName, files);
        }
        files.add(file);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<File, Map<String, List<File>>> folderEntry : mFolderToBaseNameMap.entrySet()) {
            Map<String, List<File>> baseNameMap = folderEntry.getValue();
            for (Map.Entry<String, List<File>> entry : baseNameMap.entrySet()) {
                List<File> files = entry.getValue();
                if (files.size() < 2) {
                    continue;
                }

                // Check if we have both a plain .png and a .9.png
                boolean hasPlainPng = false;
                boolean hasNinePatch = false;
                File plainPngFile = null;
                File ninePatchFile = null;

                for (File f : files) {
                    if (f.getName().endsWith(".9.png")) {
                        hasNinePatch = true;
                        ninePatchFile = f;
                    } else {
                        hasPlainPng = true;
                        plainPngFile = f;
                    }
                }

                if (hasPlainPng && hasNinePatch) {
                    String baseName = entry.getKey();
                    Location location = Location.create(ninePatchFile);
                    if (plainPngFile != null) {
                        Location secondary = Location.create(plainPngFile);
                        secondary.setMessage("This file clashes with the nine-patch file");
                        location.setSecondary(secondary);
                    }
                    context.report(
                            ICON_MIX_9PNG,
                            location,
                            String.format(
                                    "The files `%1$s.png` and `%1$s.9.png` clash; both " +
                                    "will map to `@drawable/%1$s`",
                                    baseName
                            )
                    );
                }
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }
}