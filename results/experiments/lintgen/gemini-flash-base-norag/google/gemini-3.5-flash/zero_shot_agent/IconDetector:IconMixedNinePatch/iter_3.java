package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue MIXED_9PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context) {
        File folder = context.file;
        if (folder == null) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File file : files) {
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
            String baseName = entry.getKey();
            if (pngFiles.containsKey(baseName)) {
                File ninePatchFile = entry.getValue();
                File pngFile = pngFiles.get(baseName);

                Location location = Location.create(ninePatchFile);
                Location secondary = Location.create(pngFile);
                location.setSecondary(secondary);
                secondary.setMessage("Conflicting plain PNG file here");

                context.report(
                        MIXED_9PATCH,
                        location,
                        String.format(
                                "Clashing PNG and 9-PNG files: `%s` and `%s` both map to `@drawable/%s`",
                                pngFile.getName(),
                                ninePatchFile.getName(),
                                baseName
                        )
                );
            }
        }
    }
}