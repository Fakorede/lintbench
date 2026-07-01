package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The `res/drawable` folder is intended for density-independent graphics such as " +
            "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider " +
            "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` " +
            "and `drawable-xhdpi`. If the icon **really** is density independent (for example " +
            "a solid color) you can place it in `drawable-nodpi`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        // Check if the parent folder is exactly "drawable" (no qualifier)
        File file = context.file;
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String parentName = parentFolder.getName();
        if (!parentName.equals("drawable")) {
            // Has qualifiers (e.g., drawable-mdpi, drawable-nodpi, drawable-hdpi, etc.)
            return;
        }

        // Check if the file is a bitmap
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".bmp")) {
            String message = String.format(
                    "The `%1$s` file is a bitmap file. It should be defined in `drawable-mdpi`, " +
                    "`drawable-hdpi`, `drawable-xhdpi` etc. instead of here (`drawable`). " +
                    "If the icon is density independent, consider placing it in `drawable-nodpi`.",
                    file.getName());
            context.report(ICON_LOCATION, context.getLocation(file), message);
        }
    }
}