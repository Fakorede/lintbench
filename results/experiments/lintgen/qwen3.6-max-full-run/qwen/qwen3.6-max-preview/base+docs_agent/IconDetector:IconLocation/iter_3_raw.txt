package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. " +
            "For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions " +
            "in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent " +
            "(for example a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitBinaryResource(ResourceContext context) {
        File file = context.getFile();
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && "drawable".equals(parent.getName())) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot != -1) {
                String ext = name.substring(dot + 1).toLowerCase();
                if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") ||
                    ext.equals("gif") || ext.equals("webp") || ext.equals("bmp")) {
                    context.report(ISSUE, Location.create(file),
                            "Found bitmap drawable res/drawable/" + name + " in densityless folder");
                }
            }
        }
    }
}