package com.android.tools.lint.checks;

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
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as shapes "
                    + "defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                    + "providing higher and lower resolution versions in `drawable-ldpi`, "
                    + "`drawable-hdpi` and `drawable-xhdpi`. If the icon really is density "
                    + "independent (for example a solid color) you can place it in "
                    + "`drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FOLDER))
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        if (!"drawable".equals(folderName)) {
            return;
        }

        File folder = context.getFolder();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && isBitmap(file)) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "Image defined in density-independent drawable folder");
            }
        }
    }

    private static boolean isBitmap(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return (name.endsWith(".png") && !name.endsWith(".9.png"))
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }
}