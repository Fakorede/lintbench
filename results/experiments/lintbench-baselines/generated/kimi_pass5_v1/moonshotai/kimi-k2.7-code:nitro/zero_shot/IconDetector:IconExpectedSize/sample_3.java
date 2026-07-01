package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.Density;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE);

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow " +
            "these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        FolderConfiguration configuration = context.getFolderConfiguration();
        if (configuration == null) {
            return;
        }

        DensityQualifier densityQualifier = configuration.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == null
                || density == Density.NODPI
                || density == Density.ANYDPI) {
            return;
        }

        int dpi = density.getDpiValue();
        if (dpi <= 0) {
            return;
        }

        BufferedImage image = context.getImage();
        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int expectedSize = Math.round(48f * dpi / Density.MEDIUM.getDpiValue());
        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "The image `%1$s` has size %2$dx%3$d pixels, but launcher icons for density " +
                    "`%4$s` should be %5$dx%5$d pixels.",
                    context.file.getName(),
                    width,
                    height,
                    density.getResourceValue(),
                    expectedSize);
            context.report(ICON_EXPECTED_SIZE, Location.create(context.file), message);
        }
    }
}