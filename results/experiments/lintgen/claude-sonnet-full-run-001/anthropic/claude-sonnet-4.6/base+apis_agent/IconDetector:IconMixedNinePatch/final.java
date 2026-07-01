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
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.file;

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        // Map from base name (without extension) to list of files
        Map<String, List<File>> nameToFiles = new HashMap<>();

        for (File file : files) {
            String fileName = file.getName();
            String baseName = getBaseName(fileName);
            if (baseName == null) {
                continue;
            }

            List<File> list = nameToFiles.get(baseName);
            if (list == null) {
                list = new ArrayList<>();
                nameToFiles.put(baseName, list);
            }
            list.add(file);
        }

        // Check for clashes: both file.png and file.9.png present
        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> fileList = entry.getValue();
            if (fileList.size() < 2) {
                continue;
            }

            // Check if there's both a .png and a .9.png
            File pngFile = null;
            File ninePatchFile = null;

            for (File file : fileList) {
                String name = file.getName();
                if (name.endsWith(".9.png")) {
                    ninePatchFile = file;
                } else if (name.endsWith(".png")) {
                    pngFile = file;
                }
            }

            if (pngFile != null && ninePatchFile != null) {
                String baseName = entry.getKey();
                Location location = Location.create(ninePatchFile);
                Location secondary = Location.create(pngFile);
                secondary.setMessage("This file clashes with the nine-patch file");
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

    /**
     * Returns the base name for a drawable file, stripping both .9.png and .png extensions.
     * For example:
     *   "foo.9.png" -> "foo"
     *   "foo.png"   -> "foo"
     *   "foo.xml"   -> null (not a PNG)
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