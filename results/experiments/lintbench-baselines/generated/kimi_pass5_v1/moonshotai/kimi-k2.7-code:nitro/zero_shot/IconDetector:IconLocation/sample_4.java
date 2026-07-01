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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.ResourceFolderDetector {

    private static final String MORE_INFO_URL =
            "https://developer.android.com/guide/practices/screens_support.html";

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                    + "consider providing higher and lower resolution versions in "
                    + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                    + "**really** is density independent (for example a solid color) you can "
                    + "place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE))
            .setMoreInfo(MORE_INFO_URL);

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(ResourceContext context, File folder) {
        if (!"drawable".equals(folder.getName())) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String name = file.getName();
            String lower = name.toLowerCase(Locale.US);

            if (lower.endsWith(".xml")) {
                continue;
            }

            if (isBitmapFile(lower)) {
                Location location = Location.create(file);
                context.report(ICON_LOCATION, location,
                        "The image `" + name
                                + "` should probably be in a density-specific folder, not `drawable`");
            }
        }
    }

    private static boolean isBitmapFile(String lowerCaseFileName) {
        if (lowerCaseFileName.endsWith(".9.png")) {
            return true;
        }

        int dot = lowerCaseFileName.lastIndexOf('.');
        if (dot == -1) {
            return false;
        }

        String extension = lowerCaseFileName.substring(dot);
        return IMAGE_EXTENSIONS.contains(extension);
    }
}