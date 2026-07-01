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

    // Map from drawable folder path to map of base name -> list of files
    private final Map<File, Map<String, List<File>>> mFolderToFiles = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderToFiles.clear();
    }

    @Override
    public void run(@NonNull Context context) {
        // This detector does all its work in afterCheckRootProject
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        // We want to look at all files in drawable folders
        File parent = file.getParentFile();
        if (parent != null) {
            String parentName = parent.getName();
            if (parentName.equals("drawable") || parentName.startsWith("drawable-")) {
                String name = file.getName();
                if (name.endsWith(".png")) {
                    // Record this file
                    Map<String, List<File>> nameMap = mFolderToFiles.get(parent);
                    if (nameMap == null) {
                        nameMap = new HashMap<>();
                        mFolderToFiles.put(parent, nameMap);
                    }

                    // Get the base name (resource name)
                    String baseName = getResourceName(name);
                    List<File> files = nameMap.get(baseName);
                    if (files == null) {
                        files = new ArrayList<>();
                        nameMap.put(baseName, files);
                    }
                    files.add(file);
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<File, Map<String, List<File>>> folderEntry : mFolderToFiles.entrySet()) {
            Map<String, List<File>> nameMap = folderEntry.getValue();
            for (Map.Entry<String, List<File>> nameEntry : nameMap.entrySet()) {
                List<File> files = nameEntry.getValue();
                if (files.size() > 1) {
                    // Check if we have both a .png and a .9.png
                    boolean hasNinePatch = false;
                    boolean hasRegularPng = false;
                    File ninePatchFile = null;
                    File regularPngFile = null;

                    for (File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".9.png")) {
                            hasNinePatch = true;
                            ninePatchFile = file;
                        } else if (name.endsWith(".png")) {
                            hasRegularPng = true;
                            regularPngFile = file;
                        }
                    }

                    if (hasNinePatch && hasRegularPng) {
                        String baseName = nameEntry.getKey();
                        Location location = Location.create(regularPngFile);
                        Location secondary = Location.create(ninePatchFile);
                        secondary.setMessage("Nine-patch file here");
                        location.setSecondary(secondary);

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
    }

    /**
     * Returns the resource name for a given drawable file name.
     * For example, "foo.9.png" returns "foo", and "foo.png" returns "foo".
     */
    private static String getResourceName(@NonNull String fileName) {
        // Strip extension
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }
}