package com.android.tools.lint.checks;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE));

    private static final Map<Density, Integer> EXPECTED_SIZES = new EnumMap<Density, Integer>(Density.class);
    static {
        EXPECTED_SIZES.put(Density.LOW, 36);
        EXPECTED_SIZES.put(Density.MEDIUM, 48);
        EXPECTED_SIZES.put(Density.HIGH, 72);
        EXPECTED_SIZES.put(Density.XHIGH, 96);
        EXPECTED_SIZES.put(Density.XXHIGH, 144);
        EXPECTED_SIZES.put(Density.XXXHIGH, 192);
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        String name = file.getName();
        if (!name.endsWith(".png") && !name.endsWith(".webp")) {
            return;
        }

        if (!name.startsWith("ic_launcher")) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.MIPMAP && folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null || config.getDensityQualifier() == null) {
            return;
        }

        Density density = config.getDensityQualifier().getValue();
        if (density == null) {
            return;
        }

        Integer expected = EXPECTED_SIZES.get(density);
        if (expected == null) {
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            return;
        }

        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width != expected || height != expected) {
            String message = String.format(
                    "Expected launcher icon size %1$dx%1$d for %2$s but was %3$dx%4$d",
                    expected, density.getResourceValue(), width, height);
            context.report(ISSUE, Location.create(file), message);
        }
    }
}