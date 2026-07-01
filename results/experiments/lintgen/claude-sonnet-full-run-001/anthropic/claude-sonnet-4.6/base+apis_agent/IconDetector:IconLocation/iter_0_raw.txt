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
        // Check if the file is in the plain "drawable" folder (no density qualifier)
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();

        // We only care about the plain "drawable" folder (no qualifiers like -mdpi, -hdpi, etc.)
        if (folderName.equals("drawable")) {
            String fileName = file.getName().toLowerCase();
            // Check if it's a bitmap file
            if (fileName.endsWith(".png")
                    || fileName.endsWith(".jpg")
                    || fileName.endsWith(".jpeg")
                    || fileName.endsWith(".gif")
                    || fileName.endsWith(".webp")) {
                context.report(
                        ICON_LOCATION,
                        context.getLocation(file),
                        "Found bitmap drawable `res/drawable/" + file.getName() + "` in " +
                        "densityless folder; move it to `drawable-mdpi` and consider " +
                        "providing higher and lower resolution versions in `drawable-ldpi`, " +
                        "`drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is " +
                        "density independent (for example a solid color) you can place it " +
                        "in `drawable-nodpi`."
                );
            }
        }
    }
}