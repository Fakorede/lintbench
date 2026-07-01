package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The `res/drawable` folder is intended for density-independent graphics such as " +
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
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> BITMAP_EXTENSIONS = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp"
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // We only care about bitmap files placed in the drawable folder,
        // not XML files (which are legitimately density-independent).
        // This method is called for XML files; bitmap files are handled via visitFile below.
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String fileName = file.getName();

        // Check if this file is in the plain "drawable" folder (density-independent)
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String parentName = parentDir.getName();

        // We only flag files in the exact "drawable" folder (no qualifiers like -mdpi, -hdpi, etc.)
        if (!parentName.equals("drawable")) {
            return;
        }

        // Check if the file is a bitmap image
        String lowerName = fileName.toLowerCase();
        boolean isBitmap = false;
        for (String ext : BITMAP_EXTENSIONS) {
            if (lowerName.endsWith("." + ext)) {
                isBitmap = true;
                break;
            }
        }

        if (isBitmap) {
            Location location = Location.create(file);
            context.report(
                    ICON_LOCATION,
                    location,
                    "The `res/drawable` folder is intended for density-independent graphics " +
                    "such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` " +
                    "and consider providing higher and lower resolution versions in " +
                    "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon " +
                    "**really** is density independent (for example a solid color) you can " +
                    "place it in `drawable-nodpi`."
            );
        }
    }
}