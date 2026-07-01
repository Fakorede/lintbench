package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent (for example a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final List<String> DENSITY_QUALIFIERS = Arrays.asList("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "tvdpi", "nodpi", "anydpi");

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String folderName = parent.getName();
        if (!folderName.startsWith("drawable")) {
            return;
        }

        String[] qualifiers = folderName.split("-");
        boolean hasDensity = false;
        for (int i = 1; i < qualifiers.length; i++) {
            if (DENSITY_QUALIFIERS.contains(qualifiers[i])) {
                hasDensity = true;
                break;
            }
        }

        if (!hasDensity) {
            context.report(ISSUE, Location.create(file),
                "Found bitmap drawable `res/" + folderName + "/" + name + "` in densityless folder");
        }
    }
}