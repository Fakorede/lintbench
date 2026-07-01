package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You " +
            "should follow these conventions to make sure your icons fit in with the " +
            "overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    // Expected sizes for launcher icons (ic_launcher) per density
    private static final Map<Density, int[]> LAUNCHER_ICON_SIZES;

    // Expected sizes for action bar / menu icons per density
    private static final Map<Density, int[]> ACTION_BAR_ICON_SIZES;

    // Expected sizes for notification icons per density
    private static final Map<Density, int[]> NOTIFICATION_ICON_SIZES;

    // Expected sizes for small / contextual icons per density
    private static final Map<Density, int[]> SMALL_ICON_SIZES;

    static {
        LAUNCHER_ICON_SIZES = new HashMap<>();
        LAUNCHER_ICON_SIZES.put(Density.LOW,     new int[]{36,  36});
        LAUNCHER_ICON_SIZES.put(Density.MEDIUM,  new int[]{48,  48});
        LAUNCHER_ICON_SIZES.put(Density.HIGH,    new int[]{72,  72});
        LAUNCHER_ICON_SIZES.put(Density.XHIGH,   new int[]{96,  96});
        LAUNCHER_ICON_SIZES.put(Density.XXHIGH,  new int[]{144, 144});
        LAUNCHER_ICON_SIZES.put(Density.XXXHIGH, new int[]{192, 192});

        ACTION_BAR_ICON_SIZES = new HashMap<>();
        ACTION_BAR_ICON_SIZES.put(Density.LOW,     new int[]{24, 24});
        ACTION_BAR_ICON_SIZES.put(Density.MEDIUM,  new int[]{32, 32});
        ACTION_BAR_ICON_SIZES.put(Density.HIGH,    new int[]{48, 48});
        ACTION_BAR_ICON_SIZES.put(Density.XHIGH,   new int[]{64, 64});
        ACTION_BAR_ICON_SIZES.put(Density.XXHIGH,  new int[]{96, 96});
        ACTION_BAR_ICON_SIZES.put(Density.XXXHIGH, new int[]{128, 128});

        NOTIFICATION_ICON_SIZES = new HashMap<>();
        NOTIFICATION_ICON_SIZES.put(Density.LOW,     new int[]{18, 18});
        NOTIFICATION_ICON_SIZES.put(Density.MEDIUM,  new int[]{24, 24});
        NOTIFICATION_ICON_SIZES.put(Density.HIGH,    new int[]{36, 36});
        NOTIFICATION_ICON_SIZES.put(Density.XHIGH,   new int[]{48, 48});
        NOTIFICATION_ICON_SIZES.put(Density.XXHIGH,  new int[]{72, 72});
        NOTIFICATION_ICON_SIZES.put(Density.XXXHIGH, new int[]{96, 96});

        SMALL_ICON_SIZES = new HashMap<>();
        SMALL_ICON_SIZES.put(Density.LOW,     new int[]{16, 16});
        SMALL_ICON_SIZES.put(Density.MEDIUM,  new int[]{16, 16});
        SMALL_ICON_SIZES.put(Density.HIGH,    new int[]{24, 24});
        SMALL_ICON_SIZES.put(Density.XHIGH,   new int[]{32, 32});
        SMALL_ICON_SIZES.put(Density.XXHIGH,  new int[]{48, 48});
        SMALL_ICON_SIZES.put(Density.XXXHIGH, new int[]{64, 64});
    }

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();

        // Only process PNG, WebP, GIF, JPG image files
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".webp")
                && !lowerName.endsWith(".gif") && !lowerName.endsWith(".jpg")
                && !lowerName.endsWith(".jpeg")) {
            return;
        }

        // Determine the resource folder type
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        // Determine the density from the folder name
        File parentFile = file.getParentFile();
        String folderName = parentFile != null ? parentFile.getName() : "";
        Density density = getFolderDensity(folderName);
        if (density == null) {
            return;
        }

        // Determine the icon type from the file name (without extension)
        String baseName = getBaseName(fileName);
        Map<Density, int[]> expectedSizes = getExpectedSizes(baseName);
        if (expectedSizes == null) {
            return;
        }

        int[] expected = expectedSizes.get(density);
        if (expected == null) {
            return;
        }

        // Read the actual image dimensions
        int[] actual = getImageDimension(file);
        if (actual == null) {
            return;
        }

        if (actual[0] != expected[0] || actual[1] != expected[1]) {
            String message = String.format(
                    "Incorrect icon size for `%1$s` in `%2$s`: expected %3$dx%4$d, but was %5$dx%6$d",
                    fileName,
                    folderName,
                    expected[0], expected[1],
                    actual[0], actual[1]
            );
            Location location = Location.create(file);
            context.report(ICON_EXPECTED_SIZE, location, message);
        }
    }

    /**
     * Returns the density for the given resource folder name, or null if it cannot be determined.
     */
    private static Density getFolderDensity(@NonNull String folderName) {
        // Split on '-' to find density qualifier tokens
        String[] parts = folderName.split("-");
        for (String part : parts) {
            switch (part) {
                case "xxxhdpi": return Density.XXXHIGH;
                case "xxhdpi":  return Density.XXHIGH;
                case "xhdpi":   return Density.XHIGH;
                case "hdpi":    return Density.HIGH;
                case "mdpi":    return Density.MEDIUM;
                case "ldpi":    return Density.LOW;
            }
        }
        // Default (no density qualifier) for drawable or mipmap folders is mdpi
        if (folderName.equals("drawable") || folderName.equals("mipmap")) {
            return Density.MEDIUM;
        }
        // For compound qualifiers without a density part, treat as mdpi if it's a drawable/mipmap folder
        if (folderName.startsWith("drawable-") || folderName.startsWith("mipmap-")) {
            return Density.MEDIUM;
        }
        return null;
    }

    /**
     * Returns the expected size map for the given icon base name, or null if unknown.
     */
    private static Map<Density, int[]> getExpectedSizes(@NonNull String baseName) {
        String lower = baseName.toLowerCase();
        if (lower.startsWith("ic_launcher") || lower.equals("icon")) {
            return LAUNCHER_ICON_SIZES;
        } else if (lower.startsWith("ic_menu_") || lower.startsWith("ic_action_")
                || lower.startsWith("ic_ab_")) {
            return ACTION_BAR_ICON_SIZES;
        } else if (lower.startsWith("ic_stat_") || lower.startsWith("ic_notification_")) {
            return NOTIFICATION_ICON_SIZES;
        } else if (lower.startsWith("ic_dialog_") || lower.startsWith("ic_small_")) {
            return SMALL_ICON_SIZES;
        }
        // Try to detect by common naming patterns
        if (lower.contains("launcher")) {
            return LAUNCHER_ICON_SIZES;
        }
        return null;
    }

    /**
     * Returns the base name (without extension) of the given file name.
     */
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    /**
     * Reads the image dimensions from the given file, or returns null on failure.
     */
    private static int[] getImageDimension(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (IOException e) {
            // Ignore; we simply won't check this file
        }
        return null;
    }
}