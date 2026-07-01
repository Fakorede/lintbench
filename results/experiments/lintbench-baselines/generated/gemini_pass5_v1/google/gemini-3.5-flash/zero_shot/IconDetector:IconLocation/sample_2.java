package com.android.tools.lint.checks;

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
        "The res/drawable folder is intended for density-independent graphics such as " +
        "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider " +
        "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` " +
        "and `drawable-xhdpi`. If the icon **really** is density independent (for example " +
        "a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public void run(Context context) {
        File file = context.file;
        if (!isBitmap(file)) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        String[] segments = parentName.split("-");
        if (segments.length > 0 && segments[0].equals("drawable")) {
            boolean hasDensity = false;
            for (int i = 1; i < segments.length; i++) {
                if (isDensityQualifier(segments[i])) {
                    hasDensity = true;
                    break;
                }
            }

            if (!hasDensity) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "Found bitmap file in density-independent folder `" + parentName + "`; should be in a density-specific folder (e.g. `drawable-mdpi` or `drawable-nodpi` if truly density-independent)"
                );
            }
        }
    }

    private boolean isBitmap(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".png") ||
               name.endsWith(".jpg") ||
               name.endsWith(".jpeg") ||
               name.endsWith(".gif") ||
               name.endsWith(".webp");
    }

    private boolean isDensityQualifier(String segment) {
        if (segment.equals("ldpi") ||
            segment.equals("mdpi") ||
            segment.equals("hdpi") ||
            segment.equals("xhdpi") ||
            segment.equals("xxhdpi") ||
            segment.equals("xxxhdpi") ||
            segment.equals("nodpi") ||
            segment.equals("tvdpi") ||
            segment.equals("anydpi")) {
            return true;
        }
        if (segment.endsWith("dpi")) {
            String num = segment.substring(0, segment.length() - 3);
            if (num.isEmpty()) {
                return false;
            }
            for (int i = 0; i < num.length(); i++) {
                if (!Character.isDigit(num.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}