package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Locale;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon really is density independent (for example a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE),
            "https://developer.android.com/guide/practices/screens_support.html"
    );

    @Override
    public void checkResourceFolder(@NonNull ResourceFolder context) {
        if (context.getType() != ResourceFolderType.DRAWABLE) {
            return;
        }
        if (!"drawable".equals(context.getName())) {
            return;
        }
        for (File file : context.getFiles()) {
            String name = file.getName();
            if (isBitmap(name)) {
                Location location = Location.create(file);
                context.report(
                        ICON_LOCATION,
                        location,
                        "The resource `"
                                + name
                                + "` appears in the density-independent `drawable` folder; bitmaps should normally be placed in a density-specific folder such as `drawable-mdpi`, `drawable-hdpi`, etc. If this icon is truly density independent, use `drawable-nodpi`.");
            }
        }
    }

    private static boolean isBitmap(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".9.png");
    }
}