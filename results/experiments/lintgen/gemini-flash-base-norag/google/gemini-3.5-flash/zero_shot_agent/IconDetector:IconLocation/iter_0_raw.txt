package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

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
            Scope.BINARY_RESOURCE_SCOPE
        )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        File file = context.file;
        String name = file.getName();
        if (!isBitmap(name)) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        if (!isDensityFolder(parentName)) {
            context.report(
                ISSUE,
                Location.create(file),
                "Found bitmap resource in density-independent folder `" + parentName + "`"
            );
        }
    }

    private static boolean isBitmap(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png") ||
               lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") ||
               lower.endsWith(".gif") ||
               lower.endsWith(".webp");
    }

    private static boolean isDensityFolder(@NonNull String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") ||
                segment.equals("mdpi") ||
                segment.equals("tvdpi") ||
                segment.equals("hdpi") ||
                segment.equals("xhdpi") ||
                segment.equals("xxhdpi") ||
                segment.equals("xxxhdpi") ||
                segment.equals("nodpi") ||
                segment.equals("anydpi")) {
                return true;
            }
        }
        return false;
    }
}