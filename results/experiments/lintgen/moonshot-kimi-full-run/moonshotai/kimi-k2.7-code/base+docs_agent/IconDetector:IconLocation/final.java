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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move the file to `drawable-mdpi` and "
                    + "consider providing higher and lower resolution versions in "
                    + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon really "
                    + "is density independent (for example a solid color), you can place it in "
                    + "`drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final String MESSAGE =
            "Bitmap images should not be placed in the default drawable folder; "
                    + "move to a density-specific folder (e.g. drawable-mdpi) or use drawable-nodpi";

    @Override
    public List<ResourceFolderType> getApplicableResourceFolders() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitResourceFolder(ResourceContext context) {
        File folder = context.file;
        if (folder == null || !"drawable".equals(folder.getName())) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && isBitmapFile(file)) {
                Location location = Location.create(file);
                context.report(ICON_LOCATION, location, MESSAGE);
            }
        }
    }

    private static boolean isBitmapFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.equals("png")
                || ext.equals("gif")
                || ext.equals("jpg")
                || ext.equals("jpeg")
                || ext.equals("webp")
                || ext.equals("bmp");
    }
}