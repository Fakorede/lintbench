package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ICON_MIXED_NINEPATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same drawable "
                    + "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER));

    @Override
    public void checkFolder(Context context, File folder) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File file : files) {
            if (!file.isFile()) {
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

        for (Map.Entry<String, File> entry : ninePatchFiles.entrySet()) {
            File pngFile = pngFiles.get(entry.getKey());
            if (pngFile != null) {
                File ninePatchFile = entry.getValue();
                Location location = context.getLocation(ninePatchFile);
                Location secondary = context.getLocation(pngFile);
                String message = String.format(
                        "`%1$s.png` and `%1$s.9.png` both map to `@drawable/%1$s`",
                        entry.getKey());
                context.report(ICON_MIXED_NINEPATCH,
                        location.withSecondary(secondary, "Clashing PNG"),
                        message);
            }
        }
    }
}