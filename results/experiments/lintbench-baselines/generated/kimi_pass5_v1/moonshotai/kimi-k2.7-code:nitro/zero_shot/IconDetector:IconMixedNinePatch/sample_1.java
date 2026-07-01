package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderDetector {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            7,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

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

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String baseName = name.substring(0, name.length() - ".9.png".length());
                ninePatchFiles.put(baseName, file);
            } else if (name.endsWith(".png")) {
                String baseName = name.substring(0, name.length() - ".png".length());
                pngFiles.put(baseName, file);
            }
        }

        for (Map.Entry<String, File> entry : pngFiles.entrySet()) {
            String baseName = entry.getKey();
            File ninePatchFile = ninePatchFiles.get(baseName);
            if (ninePatchFile != null) {
                File pngFile = entry.getValue();
                String message = String.format(
                        "The resources `%1$s` and `%2$s` both map to `@drawable/%3$s`",
                        pngFile.getName(), ninePatchFile.getName(), baseName);
                context.report(ISSUE, Location.create(pngFile), message);
            }
        }
    }
}