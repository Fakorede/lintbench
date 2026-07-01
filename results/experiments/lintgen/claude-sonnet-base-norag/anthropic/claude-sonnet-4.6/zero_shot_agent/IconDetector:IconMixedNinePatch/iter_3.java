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

    /** Map from drawable folder to map of base name to list of files */
    private final Map<File, Map<String, List<File>>> mFolderMap =
            new HashMap<File, Map<String, List<File>>>();

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderMap.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<File, Map<String, List<File>>> folderEntry : mFolderMap.entrySet()) {
            Map<String, List<File>> nameMap = folderEntry.getValue();
            for (Map.Entry<String, List<File>> nameEntry : nameMap.entrySet()) {
                List<File> files = nameEntry.getValue();
                if (files.size() > 1) {
                    boolean hasPng = false;
                    boolean hasNinePatch = false;
                    File pngFile = null;
                    File ninePatchFile = null;
                    for (File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".9.png")) {
                            hasNinePatch = true;
                            ninePatchFile = file;
                        } else if (name.endsWith(".png")) {
                            hasPng = true;
                            pngFile = file;
                        }
                    }
                    if (hasPng && hasNinePatch) {
                        Location location = Location.create(ninePatchFile);
                        Location secondary = Location.create(pngFile);
                        secondary.setMessage("This file clashes with the nine-patch file");
                        location.setSecondary(secondary);
                        context.report(
                                ICON_MIX_9PNG,
                                location,
                                String.format(
                                        "The files `%1$s` and `%2$s` both map to the same " +
                                        "drawable resource id `@drawable/%3$s`",
                                        ninePatchFile.getName(),
                                        pngFile.getName(),
                                        nameEntry.getKey()
                                )
                        );
                    }
                }
            }
        }
    }

    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }

    private void recordFile(@NonNull File folder, @NonNull File file) {
        String name = file.getName();
        String baseName = getBaseName(name);
        if (baseName == null) {
            return;
        }

        Map<String, List<File>> nameMap = mFolderMap.get(folder);
        if (nameMap == null) {
            nameMap = new HashMap<String, List<File>>();
            mFolderMap.put(folder, nameMap);
        }

        List<File> fileList = nameMap.get(baseName);
        if (fileList == null) {
            fileList = new ArrayList<File>();
            nameMap.put(baseName, fileList);
        }
        fileList.add(file);
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(".png")) {
            File folder = file.getParentFile();
            if (folder != null) {
                String folderName = folder.getName();
                if (folderName.startsWith("drawable")) {
                    recordFile(folder, file);
                }
            }
        }
    }
}