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

    // Expected sizes for launcher icons (ic_launcher) per density: {width, height, dp_size}
    private static final Map<Density, int[]> LAUNCHER_ICON_SIZES = new HashMap<>();
    private static final Map<Density, int[]> ACTION_BAR_ICON_SIZES = new HashMap<>();
    private static final Map<Density, int[]> NOTIFICATION_ICON_SIZES = new HashMap<>();
    private static final Map<Density, int[]> SMALL_ICON_SIZES = new HashMap<>();

    static {
        LAUNCHER_ICON_SIZES.put(Density.LOW,     new int[]{36,  36});
        LAUNCHER_ICON_SIZES.put(Density.MEDIUM,  new int[]{48,  48});
        LAUNCHER_ICON_SIZES.put(Density.HIGH,    new int[]{72,  72});
        LAUNCHER_ICON_SIZES.put(Density.XHIGH,   new int[]{96,  96});
        LAUNCHER_ICON_SIZES.put(Density.XXHIGH,  new int[]{144, 144});
        LAUNCHER_ICON_SIZES.put(Density.XXXHIGH, new int[]{192, 192});

        ACTION_BAR_ICON_SIZES.put(Density.LOW,     new int[]{24, 24});
        ACTION_BAR_ICON_SIZES.put(Density.MEDIUM,  new int[]{32, 32});
        ACTION_BAR_ICON_SIZES.put(Density.HIGH,    new int[]{48, 48});
        ACTION_BAR_ICON_SIZES.put(Density.XHIGH,   new int[]{64, 64});
        ACTION_BAR_ICON_SIZES.put(Density.XXHIGH,  new int[]{96, 96});
        ACTION_BAR_ICON_SIZES.put(Density.XXXHIGH, new int[]{128, 128});

        NOTIFICATION_ICON_SIZES.put(Density.LOW,     new int[]{18, 18});
        NOTIFICATION_ICON_SIZES.put(Density.MEDIUM,  new int[]{24, 24});
        NOTIFICATION_ICON_SIZES.put(Density.HIGH,    new int[]{36, 36});
        NOTIFICATION_ICON_SIZES.put(Density.XHIGH,   new int[]{48, 48});
        NOTIFICATION_ICON_SIZES.put(Density.XXHIGH,  new int[]{72, 72});
        NOTIFICATION_ICON_SIZES.put(Density.XXXHIGH, new int[]{96, 96});

        SMALL_ICON_SIZES.put(Density.LOW,     new int[]{16, 16});
        SMALL_ICON_SIZES.put(Density.MEDIUM,  new int[]{16, 16});
        SMALL_ICON_SIZES.put(Density.HIGH,    new int[]{24, 24});
        SMALL_ICON_SIZES.put(Density.XHIGH,   new int[]{32, 32});
        SMALL_ICON_SIZES.put(Density.XXHIGH,  new int[]{48, 48});
        SMALL_ICON_SIZES.put(Density.XXXHIGH, new int[]{64, 64});
    }

    // Map from dp size to expected pixel sizes per density
    // This allows us to check any icon given its dp size
    private static final Map<Integer, Map<Density, int[]>> DP_TO_SIZE_MAP = new HashMap<>();

    static {
        // 48dp launcher icons
        Map<Density, int[]> launcher = new HashMap<>();
        launcher.put(Density.LOW,     new int[]{36,  36});
        launcher.put(Density.MEDIUM,  new int[]{48,  48});
        launcher.put(Density.HIGH,    new int[]{72,  72});
        launcher.put(Density.XHIGH,   new int[]{96,  96});
        launcher.put(Density.XXHIGH,  new int[]{144, 144});
        launcher.put(Density.XXXHIGH, new int[]{192, 192});
        DP_TO_SIZE_MAP.put(48, launcher);

        // 32dp action bar icons
        Map<Density, int[]> actionBar = new HashMap<>();
        actionBar.put(Density.LOW,     new int[]{24, 24});
        actionBar.put(Density.MEDIUM,  new int[]{32, 32});
        actionBar.put(Density.HIGH,    new int[]{48, 48});
        actionBar.put(Density.XHIGH,   new int[]{64, 64});
        actionBar.put(Density.XXHIGH,  new int[]{96, 96});
        actionBar.put(Density.XXXHIGH, new int[]{128, 128});
        DP_TO_SIZE_MAP.put(32, actionBar);

        // 24dp notification icons
        Map<Density, int[]> notification = new HashMap<>();
        notification.put(Density.LOW,     new int[]{18, 18});
        notification.put(Density.MEDIUM,  new int[]{24, 24});
        notification.put(Density.HIGH,    new int[]{36, 36});
        notification.put(Density.XHIGH,   new int[]{48, 48});
        notification.put(Density.XXHIGH,  new int[]{72, 72});
        notification.put(Density.XXXHIGH, new int[]{96, 96});
        DP_TO_SIZE_MAP.put(24, notification);

        // 16dp small icons
        Map<Density, int[]> small = new HashMap<>();
        small.put(Density.LOW,     new int[]{12, 12});
        small.put(Density.MEDIUM,  new int[]{16, 16});
        small.put(Density.HIGH,    new int[]{24, 24});
        small.put(Density.XHIGH,   new int[]{32, 32});
        small.put(Density.XXHIGH,  new int[]{48, 48});
        small.put(Density.XXXHIGH, new int[]{64, 64});
        DP_TO_SIZE_MAP.put(16, small);
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

        // Only process image files
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
        Map<Density, int[]> expectedSizes = getExpectedSizes(baseName, folderType);
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
        return null;
    }

    /**
     * Returns the expected size map for the given icon base name, or null if unknown.
     */
    private static Map<Density, int[]> getExpectedSizes(@NonNull String baseName,
            @NonNull ResourceFolderType folderType) {
        String lower = baseName.toLowerCase();

        // Mipmap folders are typically used for launcher icons
        if (folderType == ResourceFolderType.MIPMAP) {
            return LAUNCHER_ICON_SIZES;
        }

        // Check for launcher icons
        if (lower.startsWith("ic_launcher") || lower.equals("icon")
                || lower.contains("launcher")) {
            return LAUNCHER_ICON_SIZES;
        }

        // Check for action bar / menu icons
        if (lower.startsWith("ic_menu_") || lower.startsWith("ic_action_")
                || lower.startsWith("ic_ab_")) {
            return ACTION_BAR_ICON_SIZES;
        }

        // Check for notification icons
        if (lower.startsWith("ic_stat_") || lower.startsWith("ic_notification_")) {
            return NOTIFICATION_ICON_SIZES;
        }

        // Check for small / dialog icons
        if (lower.startsWith("ic_dialog_") || lower.startsWith("ic_small_")) {
            return SMALL_ICON_SIZES;
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