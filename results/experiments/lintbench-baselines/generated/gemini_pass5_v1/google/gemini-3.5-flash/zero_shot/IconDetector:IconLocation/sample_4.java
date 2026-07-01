package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as " +
            "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider " +
            "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` " +
            "and `drawable-xhdpi`. If the icon **really** is density independent (for example " +
            "a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context) {
        File folder = context.getFolder();
        if (folder == null) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folder.getName());
        if (config != null && config.getDensityQualifier() == null) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (isBitmapFile(name)) {
                        Location location = Location.create(file);
                        context.report(
                                ISSUE,
                                location,
                                "Keep bitmap files in density-specific folders (e.g. `drawable-mdpi`) " +
                                "instead of `" + folder.getName() + "`"
                        );
                    }
                }
            }
        }
    }

    private static boolean isBitmapFile(@NonNull String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }
}