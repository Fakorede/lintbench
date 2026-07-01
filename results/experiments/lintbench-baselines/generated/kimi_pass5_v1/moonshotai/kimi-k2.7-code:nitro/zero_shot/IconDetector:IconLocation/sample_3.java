package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The `res/drawable` folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move the file to `drawable-mdpi` and "
                    + "consider providing higher and lower resolution versions in `drawable-ldpi`, "
                    + "`drawable-hdpi` and `drawable-xhdpi`. If the icon really is density "
                    + "independent (for example a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesToResourceFolders() {
        return true;
    }

    @Override
    public List<String> getApplicableResourceFolders() {
        return Collections.singletonList("drawable");
    }

    @Override
    public void visitResourceFolder(@NonNull Context context, @NonNull File folder) {
        if (!"drawable".equals(folder.getName())) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && isBitmap(file.getName())) {
                Location location = Location.create(file);
                context.report(
                        ISSUE,
                        location,
                        "Bitmaps should not be placed in the density-independent `res/drawable` folder.");
            }
        }
    }

    private static boolean isBitmap(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".9.png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }
}