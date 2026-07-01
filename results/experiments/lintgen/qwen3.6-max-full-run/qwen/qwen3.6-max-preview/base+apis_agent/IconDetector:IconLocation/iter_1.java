package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;

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
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE)));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.getName().equals("drawable")) {
            String name = file.getName();
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
                String message = String.format(
                    "Found bitmap drawable `res/drawable/%1$s` in densityless folder", name);
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }
}