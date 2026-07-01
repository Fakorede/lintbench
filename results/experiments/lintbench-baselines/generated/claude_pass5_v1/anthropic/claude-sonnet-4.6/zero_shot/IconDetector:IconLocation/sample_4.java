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

import com.android.tools.lint.detector.api.Detector;

public class IconDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

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
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE, Scope.RESOURCE_FILE)
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> BITMAP_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    // Handle binary resource files (actual bitmap files)
    @Override
    public void checkBinaryResource(@NonNull Context context) {
        File file = context.file;
        String parentName = file.getParentFile() != null ? file.getParentFile().getName() : "";

        // Only flag files in the plain "drawable" folder (not drawable-mdpi, drawable-hdpi, etc.)
        if (!parentName.equals("drawable")) {
            return;
        }

        String fileName = file.getName().toLowerCase();
        boolean isBitmap = false;
        for (String ext : BITMAP_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
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

    // Handle XML resource files - check for <bitmap> elements in plain drawable folder
    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String parentName = file.getParentFile() != null ? file.getParentFile().getName() : "";

        if (!parentName.equals("drawable")) {
            return;
        }

        String rootTag = document.getDocumentElement() != null
                ? document.getDocumentElement().getTagName()
                : null;

        if ("bitmap".equals(rootTag) || "nine-patch".equals(rootTag)) {
            Location location = context.getLocation(document.getDocumentElement());
            context.report(
                    ICON_LOCATION,
                    document.getDocumentElement(),
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