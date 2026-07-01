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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as " +
            "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider " +
            "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` " +
            "and `drawable-xhdpi`. If the icon really is density independent (for example a " +
            "solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    ).setMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final Set<String> BITMAP_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico"
    ));

    @Override
    public void checkResourceFolder(@NonNull Context context,
                                    @NonNull ResourceFolderType folderType,
                                    @NonNull File folder) {
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        if (!"drawable".equals(folder.getName())) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (name.endsWith(".xml")) {
                continue;
            }

            String extension = getExtension(name);
            if (extension == null) {
                continue;
            }

            if (BITMAP_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
                Location location = Location.create(file);
                String message = String.format(
                        "The image `%1$s` is defined in the density-independent drawable folder; "
                                + "move it to a density-specific folder such as drawable-mdpi.",
                        name);
                context.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1);
    }
}