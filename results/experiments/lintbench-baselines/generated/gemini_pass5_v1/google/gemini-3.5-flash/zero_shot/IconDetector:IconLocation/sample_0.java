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
import java.util.Locale;
import org.jetbrains.annotations.NonNull;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as " +
        "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider " +
        "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` " +
        "and `drawable-xhdpi`. If the icon **really** is density independent (for example " +
        "a solid color) you can place it in `drawable-nodpi`.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent != null && parent.getName().equals("drawable")) {
            String name = file.getName().toLowerCase(Locale.US);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "Found bitmap drawable `res/drawable/" + file.getName() + "`. All bitmaps should be " +
                    "placed in density-specific folders (such as `drawable-mdpi`) or `drawable-nodpi` if they " +
                    "are density-independent."
                );
            }
        }
    }
}