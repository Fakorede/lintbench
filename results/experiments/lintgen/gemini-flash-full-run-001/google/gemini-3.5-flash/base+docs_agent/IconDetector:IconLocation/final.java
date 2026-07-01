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
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

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
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase(Locale.US);
        
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || 
            name.endsWith(".gif") || name.endsWith(".webp")) {
            
            String folderName = file.getParentFile().getName();
            if (isDensityIndependentFolder(folderName)) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "Found bitmap drawable in density-independent folder `" + folderName + "`; " +
                        "it should be moved to a density-specific folder (e.g. `drawable-mdpi` or `drawable-nodpi` if truly density-independent)"
                );
            }
        }
    }

    private boolean isDensityIndependentFolder(String folderName) {
        if (folderName.equals("drawable")) {
            return true;
        }
        if (folderName.startsWith("drawable-")) {
            String[] segments = folderName.split("-");
            for (String segment : segments) {
                if (isDensityQualifier(segment)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isDensityQualifier(String segment) {
        if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
            segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi") ||
            segment.equals("nodpi") || segment.equals("anydpi") || segment.equals("tvdpi")) {
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