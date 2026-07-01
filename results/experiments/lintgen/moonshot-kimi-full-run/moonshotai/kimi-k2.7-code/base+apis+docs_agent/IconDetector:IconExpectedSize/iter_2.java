package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String baseName = stripExtension(file.getName());
        if (!isLauncherIcon(baseName)) {
            return;
        }

        Density density = getDensity(file.getParentFile());
        if (density == null || density == Density.ANYDPI || density == Density.NODPI) {
            return;
        }

        int expectedSize = getExpectedLauncherSize(density);
        if (expectedSize <= 0) {
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
        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "The launcher icon is %dx%d px, but for density `%s` it should be %dx%d px",
                    width, height, density.getResourceValue(), expectedSize, expectedSize);
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    private static boolean isLauncherIcon(String baseName) {
        return "ic_launcher".equals(baseName) || "ic_launcher_round".equals(baseName);
    }

    private static Density getDensity(File parent) {
        if (parent == null) {
            return Density.MEDIUM;
        }
        String[] parts = parent.getName().split("-");
        if (parts.length <= 1) {
            return Density.MEDIUM;
        }
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if ("ldpi".equals(part)) {
                return Density.LOW;
            } else if ("mdpi".equals(part)) {
                return Density.MEDIUM;
            } else if ("hdpi".equals(part)) {
                return Density.HIGH;
            } else if ("xhdpi".equals(part)) {
                return Density.XHIGH;
            } else if ("xxhdpi".equals(part)) {
                return Density.XXHIGH;
            } else if ("xxxhdpi".equals(part)) {
                return Density.XXXHIGH;
            } else if ("anydpi".equals(part)) {
                return Density.ANYDPI;
            } else if ("nodpi".equals(part)) {
                return Density.NODPI;
            }
        }
        return null;
    }

    private static int getExpectedLauncherSize(@NonNull Density density) {
        if (density == Density.LOW) {
            return 36;
        } else if (density == Density.MEDIUM) {
            return 48;
        } else if (density == Density.HIGH) {
            return 72;
        } else if (density == Density.XHIGH) {
            return 96;
        } else if (density == Density.XXHIGH) {
            return 144;
        } else if (density == Density.XXXHIGH) {
            return 192;
        }
        return -1;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }
}