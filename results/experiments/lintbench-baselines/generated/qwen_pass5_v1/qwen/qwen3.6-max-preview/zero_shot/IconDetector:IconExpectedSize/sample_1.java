package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.ImageContext;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ImageScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.IMAGE_FILE_SCOPE));

    private static final Map<Density, Integer> EXPECTED_SIZES = new HashMap<>();

    static {
        EXPECTED_SIZES.put(Density.LOW, 36);
        EXPECTED_SIZES.put(Density.MEDIUM, 48);
        EXPECTED_SIZES.put(Density.HIGH, 72);
        EXPECTED_SIZES.put(Density.XHIGH, 96);
        EXPECTED_SIZES.put(Density.XXHIGH, 144);
        EXPECTED_SIZES.put(Density.XXXHIGH, 192);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitImage(ImageContext context, BufferedImage image) {
        Density density = context.getDensity();
        if (density == null || density == Density.NODPI || density == Density.ANYDPI) {
            return;
        }

        Integer expectedSize = EXPECTED_SIZES.get(density);
        if (expectedSize == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "Expected launcher icon size to be %1$dx%1$d but was %2$dx%3$d",
                    expectedSize, width, height);
            context.report(ISSUE, Location.create(context.file), message);
        }
    }
}