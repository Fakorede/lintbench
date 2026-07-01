package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. " +
        "For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions " +
        "in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent " +
        "(for example a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER)
    );

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    );

    private Context context;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        this.context = context;
    }

    @Override
    public void visitFolder(@NotNull ResourceFolderInfo folder) {
        // No-op: filtering is handled in visitFile
    }

    @Override
    public void visitFile(@NotNull ResourceFile file) {
        ResourceFolderInfo folder = file.getFolder();
        if (folder.getFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        boolean hasDensity = folder.getConfiguration().getDensityQualifier() != null
            && folder.getConfiguration().getDensityQualifier().getValue() != null;

        if (hasDensity) {
            return;
        }

        String name = file.getName();
        String lowerName = name.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                Location location = Location.create(file.getFile());
                context.report(ISSUE, location, "Found bitmap drawable `res/drawable/" + name + "` in densityless folder");
                return;
            }
        }
    }
}