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
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
        new Implementation(
            IconDetector.class,
            EnumSet.of(Scope.BINARY_RESOURCE_FILE)
        )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String fileName = file.getName().toLowerCase(Locale.US);
        
        // Only check common bitmap/raster image formats
        if (!(fileName.endsWith(".png") || fileName.endsWith(".jpg") || 
              fileName.endsWith(".jpeg") || fileName.endsWith(".gif") || 
              fileName.endsWith(".webp"))) {
            return;
        }

        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String folderName = parentFolder.getName();
        if (folderName.equals("drawable") || folderName.startsWith("drawable-")) {
            String[] segments = folderName.split("-");
            boolean hasDensity = false;
            for (String segment : segments) {
                if (isDensityQualifier(segment)) {
                    hasDensity = true;
                    break;
                }
            }

            if (!hasDensity) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "Found bitmap/raster image in density-independent folder; it should be " +
                    "moved to a density-specific folder (such as `drawable-mdpi`) or `drawable-nodpi`"
                );
            }
        }
    }

    private boolean isDensityQualifier(String segment) {
        if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
            segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi") ||
            segment.equals("tvdpi") || segment.equals("nodpi") || segment.equals("anydpi")) {
            return true;
        }
        if (segment.endsWith("dpi") && segment.length() > 3) {
            for (int i = 0; i < segment.length() - 3; i++) {
                if (!Character.isDigit(segment.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}