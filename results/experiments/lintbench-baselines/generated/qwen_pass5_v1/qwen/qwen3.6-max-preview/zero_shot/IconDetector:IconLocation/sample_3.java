package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. " +
        "For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in " +
        "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent " +
        "(for example a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
        "png", "jpg", "jpeg", "gif", "bmp", "webp"
    );

    @Override
    public void visitBinaryResource(@NonNull Context context, @NonNull ResourceFolderInfo folderInfo, @NonNull String name, @NonNull File file) {
        if (folderInfo.getFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        if (!folderInfo.getQualifiers().isEmpty()) {
            return;
        }

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            String extension = name.substring(dotIndex + 1).toLowerCase();
            if (IMAGE_EXTENSIONS.contains(extension)) {
                context.report(ISSUE, Location.create(file),
                    "Found bitmap drawable res/drawable/" + name + " in densityless folder");
            }
        }
    }
}