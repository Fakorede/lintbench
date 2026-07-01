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
import java.util.Locale;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                    + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                    + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                    + "a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.OTHER_SCOPE)
    );

    @Override
    public void run(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        for (File resFolder : context.getProject().getResourceFolders()) {
            File drawableFolder = new File(resFolder, "drawable");
            if (drawableFolder.isDirectory()) {
                File[] files = drawableFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (isBitmapFile(name)) {
                            Location location = Location.create(file);
                            context.report(ISSUE, location, "Found bitmap file in density-independent `drawable` folder; should be in `drawable-mdpi` or similar");
                        }
                    }
                }
            }
        }
    }

    private boolean isBitmapFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }
}