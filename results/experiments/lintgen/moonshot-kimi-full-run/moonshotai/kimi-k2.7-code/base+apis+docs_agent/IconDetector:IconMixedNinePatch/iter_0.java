package com.android.tools.lint.checks;

import androidx.annotation.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final String PNG_EXTENSION = ".png";
    private static final String NINE_PATCH_EXTENSION = ".9.png";

    private Map<String, List<File>> mDrawableFiles;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mDrawableFiles = new HashMap<>();
    }

    @Override
    public void visitResourceFolder(@NonNull ResourceContext context, @NonNull File folder) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            String baseName = null;

            if (name.endsWith(NINE_PATCH_EXTENSION)) {
                baseName = name.substring(0, name.length() - NINE_PATCH_EXTENSION.length());
            } else if (name.endsWith(PNG_EXTENSION)) {
                baseName = name.substring(0, name.length() - PNG_EXTENSION.length());
            }

            if (baseName != null) {
                mDrawableFiles
                        .computeIfAbsent(baseName, k -> new ArrayList<>())
                        .add(file);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, List<File>> entry : mDrawableFiles.entrySet()) {
            List<File> files = entry.getValue();

            File pngFile = null;
            File ninePatchFile = null;

            for (File file : files) {
                if (file.getName().endsWith(NINE_PATCH_EXTENSION)) {
                    ninePatchFile = file;
                } else {
                    pngFile = file;
                }
            }

            if (pngFile != null && ninePatchFile != null) {
                String baseName = entry.getKey();
                String message = String.format(
                        "The resource `@drawable/%1$s` is defined both as `%2$s.png` and `%2$s.9.png`; "
                                + "this will cause only one of them to be picked up at runtime",
                        baseName, baseName);
                context.report(ISSUE, Location.create(pngFile), message);
            }
        }

        mDrawableFiles = null;
    }

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same drawable "
                    + "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );
}