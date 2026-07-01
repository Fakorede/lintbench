package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final Category CATEGORY_ICONS =
            Category.create("Icons", Category.USABILITY, 80);

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The `res/drawable` folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move them to `drawable-mdpi` and "
                    + "consider providing higher and lower resolution versions in "
                    + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                    + "really is density independent (for example a solid color) you can place "
                    + "it in `drawable-nodpi`.",
            CATEGORY_ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
        if (!folder.getName().equals(ResourceFolderType.DRAWABLE.getName())) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String name = file.getName();
            String lower = name.toLowerCase();
            if (lower.endsWith(".xml") || lower.endsWith(".gitignore")) {
                continue;
            }

            if (isBitmap(lower)) {
                Location location = Location.create(file);
                context.report(
                        ISSUE,
                        location,
                        "The image `" + name + "` is defined in the density-independent "
                                + "`drawable` folder. For bitmaps, move it to a density-specific "
                                + "folder (e.g. `drawable-mdpi`) or `drawable-nodpi` if it is "
                                + "truly density independent."
                );
            }
        }
    }

    private static boolean isBitmap(String lower) {
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".9.png");
    }
}