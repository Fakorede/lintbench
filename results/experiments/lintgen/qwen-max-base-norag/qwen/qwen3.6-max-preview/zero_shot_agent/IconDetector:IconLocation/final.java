package com.android.tools.lint.checks;

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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
        "IconLocation",
        "Image defined in density-independent drawable folder",
        "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. " +
        "For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in " +
        "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent " +
        "(for example a solid color) you can place it in `drawable-nodpi`.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final Set<String> BITMAP_EXTENSIONS = new HashSet<>(Arrays.asList(
        "png", "jpg", "jpeg", "gif", "bmp", "webp"
    ));

    @Override
    public Collection<String> getApplicableFiles() {
        return BITMAP_EXTENSIONS;
    }

    @Override
    public void visitFile(Context context, File file) {
        File parent = file.getParentFile();
        if (parent != null && parent.getName().equals("drawable")) {
            context.report(ISSUE, Location.create(file),
                "Found bitmap drawable res/drawable/" + file.getName() + " in densityless folder");
        }
    }
}