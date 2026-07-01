package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE)
    );

    @Nullable
    @Override
    public Collection<ResourceFolderType> getApplicableResourceFolderTypes() {
        return EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public void visitBinaryResource(@NotNull Context context) {
        File file = context.file;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".png") && !name.endsWith(".webp") && !name.endsWith(".jpg") && !name.endsWith(".gif")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfigForFolder(parent.getName());
        if (config == null) {
            return;
        }

        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == null || density == Density.NODPI || density == Density.ANYDPI) {
            return;
        }

        int expectedSize = getExpectedSize(density);
        if (expectedSize <= 0) {
            return;
        }

        Dimension actualSize = getImageSize(file);
        if (actualSize == null) {
            return;
        }

        if (actualSize.width != expectedSize || actualSize.height != expectedSize) {
            String message = String.format(
                    "Incorrect icon size for %s: expected %dx%d, but was %dx%d",
                    density.getResourceValue(),
                    expectedSize, expectedSize,
                    actualSize.width, actualSize.height
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static int getExpectedSize(@NotNull Density density) {
        switch (density) {
            case LDPI: return 36;
            case MDPI: return 48;
            case HDPI: return 72;
            case XHDPI: return 96;
            case XXHDPI: return 144;
            case XXXHDPI: return 192;
            default: return -1;
        }
    }

    @Nullable
    private static Dimension getImageSize(@NotNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (Exception ignored) {
            // ImageIO can throw exceptions for unsupported formats or corrupted files
        }
        return null;
    }
}