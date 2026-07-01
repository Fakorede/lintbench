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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ICON_LOCATION = Issue.create(
            "IconLocation",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as "
                    + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                    + "providing higher and lower resolution versions in `drawable-ldpi`, "
                    + "`drawable-hdpi` and `drawable-xhdpi`. If the icon really is density independent "
                    + "(for example a solid color) you can place it in `drawable-nodpi`.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        // Only the plain drawable folder is intended for density-independent drawables.
        if (!"drawable".equals(context.getFile().getParentFile().getName())) {
            return;
        }

        Location location = Location.create(context.getFile());
        context.report(ICON_LOCATION, location,
                "Image defined in density-independent drawable folder");
    }
}