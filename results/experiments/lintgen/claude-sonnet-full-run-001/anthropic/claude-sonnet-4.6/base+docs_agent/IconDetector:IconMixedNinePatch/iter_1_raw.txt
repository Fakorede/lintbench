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

public class IconDetector extends Detector {

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
                    Scope.ALL_RESOURCES_SCOPE
            )
    );

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Get the resource directories and scan them
        File resourceDir = context.getProject().getResourceFolders().isEmpty()
                ? null
                : context.getProject().getResourceFolders().get(0);

        if (resourceDir == null || !resourceDir.isDirectory()) {
            return;
        }

        File[] folders = resourceDir.listFiles();
        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            if (!folder.isDirectory()) {
                continue;
            }
            String folderName = folder.getName();
            // Only check drawable folders
            if (!folderName.equals("drawable") && !folderName.startsWith("drawable-")) {
                continue;
            }

            checkFolder(context, folder);
        }
    }

    private void checkFolder(@NonNull Context context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

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

    private static String getResourceBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }
}