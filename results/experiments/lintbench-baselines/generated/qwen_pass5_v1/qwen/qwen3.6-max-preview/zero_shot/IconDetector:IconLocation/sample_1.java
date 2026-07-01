package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent (for example a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE)
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitBinaryResource(@NotNull Context context) {
        File file = context.getFile();
        String name = file.getName().toLowerCase(Locale.US);
        boolean isBitmap = name.endsWith(".png") || name.endsWith(".jpg") ||
                           name.endsWith(".jpeg") || name.endsWith(".gif") ||
                           name.endsWith(".bmp") || name.endsWith(".webp");
        if (!isBitmap) {
            return;
        }

        if (context.getConfiguration().getDensityQualifier() == null) {
            String message = String.format("Found bitmap drawable res/drawable/%s in densityless folder", name);
            context.report(ISSUE, Location.create(file), message);
        }
    }
}