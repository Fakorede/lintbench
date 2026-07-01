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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements Detector.FileScanner {

    public static final Issue ICON_MIXED_9PATCH = Issue.create(
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
    public void run(@NonNull Context context) {
        // This detector works by scanning resource directories for clashing files.
        // The actual logic is in afterCheckProject.
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        File resourceDir = context.getProject().getResourceFolders() != null
                ? null : null;

        List<File> resDirs = context.getProject().getResourceFolders();
        if (resDirs == null || resDirs.isEmpty()) {
            return;
        }

        for (File resDir : resDirs) {
            if (!resDir.exists() || !resDir.isDirectory()) {
                continue;
            }

            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }

            for (File folder : folders) {
                String folderName = folder.getName();
                // Only look at drawable folders
                if (!folderName.equals("drawable") && !folderName.startsWith("drawable-")) {
                    continue;
                }

                if (!folder.isDirectory()) {
                    continue;
                }

                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }

                // Map from base name (without extension) to list of files
                Map<String, List<File>> nameToFiles = new HashMap<>();

                for (File file : files) {
                    String fileName = file.getName();
                    String baseName = getBaseName(fileName);
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

                // Check for clashes: both file.png and file.9.png present
                for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
                    String baseName = entry.getKey();
                    List<File> fileList = entry.getValue();

                    if (fileList.size() < 2) {
                        continue;
                    }

                    // Check if there's both a .png and a .9.png
                    File pngFile = null;
                    File ninePatchFile = null;

                    for (File file : fileList) {
                        String fileName = file.getName();
                        if (fileName.endsWith(".9.png")) {
                            ninePatchFile = file;
                        } else if (fileName.endsWith(".png")) {
                            pngFile = file;
                        }
                    }

                    if (pngFile != null && ninePatchFile != null) {
                        String message = String.format(
                                "The files `%1$s` and `%2$s` both map to the same drawable " +
                                "resource id `@drawable/%3$s`",
                                pngFile.getName(),
                                ninePatchFile.getName(),
                                baseName);

                        Location location = Location.create(pngFile);
                        Location secondary = Location.create(ninePatchFile);
                        secondary.setMessage("Nine-patch file");
                        location.setSecondary(secondary);

                        context.report(ICON_MIXED_9PATCH, location, message);
                    }
                }
            }
        }
    }

    /**
     * Returns the base name (resource name) for a drawable file.
     * For "file.png" returns "file".
     * For "file.9.png" returns "file".
     * For other files returns null.
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