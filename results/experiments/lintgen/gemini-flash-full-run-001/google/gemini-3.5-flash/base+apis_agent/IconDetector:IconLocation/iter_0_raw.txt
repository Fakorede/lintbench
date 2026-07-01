package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import java.io.File;

public class IconDetector extends Detector implements BinaryResourceScanner {

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
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_SCOPE)
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent != null) {
            String parentName = parent.getName();
            if ("drawable".equals(parentName) || "mipmap".equals(parentName)) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            "Found bitmap/raster image in density-independent folder `" + parentName + "`; should be in a density-specific folder (like `drawable-mdpi` or `drawable-nodpi` if density-independent)"
                    );
                }
            }
        }
    }
}