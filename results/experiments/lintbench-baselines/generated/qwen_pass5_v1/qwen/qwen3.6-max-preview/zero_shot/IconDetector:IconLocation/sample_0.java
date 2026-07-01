package com.android.tools.lint.checks;

import com.android.resources.DensityQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceQualifier;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> getApplicableBinaryResourceFiles() {
        return Arrays.asList("png", "jpg", "jpeg", "gif", "bmp", "webp");
    }

    @Override
    public void visitBinaryResource(@NotNull ResourceContext context) {
        ResourceFolder folder = context.getResourceFolder();
        if (folder == null || folder.getFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        boolean hasDensityQualifier = false;
        for (ResourceQualifier qualifier : folder.getQualifiers()) {
            if (qualifier instanceof DensityQualifier) {
                hasDensityQualifier = true;
                break;
            }
        }

        if (!hasDensityQualifier) {
            String message = String.format(
                    "Found bitmap drawable `%1$s` in densityless folder",
                    context.file.getName());
            context.report(ISSUE, Location.create(context.file), message);
        }
    }
}