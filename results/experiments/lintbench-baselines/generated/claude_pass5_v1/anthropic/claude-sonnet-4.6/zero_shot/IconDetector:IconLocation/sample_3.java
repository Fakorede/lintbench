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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final Collection<String> BITMAP_EXTENSIONS = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp"
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();

        // Only flag files in the plain "drawable" folder (no qualifiers)
        if (!folderName.equals("drawable")) {
            return;
        }

        // Check if the file is a bitmap image
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        if (BITMAP_EXTENSIONS.contains(extension)) {
            Location location = Location.create(file);
            context.report(
                    ICON_LOCATION,
                    location,
                    "Found bitmap drawable `res/drawable/" + fileName + "` in densityless folder; " +
                    "Should be in a density-specific folder (`drawable-mdpi`, etc.)"
            );
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // No XML document checks needed for this detector
    }
}