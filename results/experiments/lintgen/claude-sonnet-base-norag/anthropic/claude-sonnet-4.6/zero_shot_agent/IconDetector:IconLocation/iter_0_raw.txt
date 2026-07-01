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

/**
 * Checks for bitmap images placed in the density-independent drawable folder.
 */
public class IconDetector extends ResourceXmlDetector {

    /** Bitmaps placed in density-independent drawable folder */
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
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
            ))
            .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> BITMAP_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    );

    /** Constructs a new {@link IconDetector} */
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

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (isBitmapFile(file)) {
            File folder = file.getParentFile();
            if (folder != null) {
                String folderName = folder.getName();
                // Check if this is the plain "drawable" folder (no qualifiers)
                if (folderName.equals("drawable")) {
                    Location location = Location.create(file);
                    context.report(
                            ICON_LOCATION,
                            location,
                            "Found bitmap drawable `res/drawable/" + file.getName() + "` in " +
                            "a density-independent drawable folder; move it to `drawable-mdpi` " +
                            "and consider providing higher and lower resolution versions in " +
                            "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the " +
                            "icon **really** is density independent (for example a solid color) " +
                            "you can place it in `drawable-nodpi`."
                    );
                }
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // We handle binary files in beforeCheckFile; XML files in drawable are fine
        // (they are typically density-independent shapes/selectors/etc.)
    }

    /**
     * Returns true if the given file is a bitmap file based on its extension.
     */
    private static boolean isBitmapFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        for (String ext : BITMAP_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}