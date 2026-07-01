package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import java.awt.Dimension;
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

    // Expected sizes for launcher icons by density qualifier
    private static final Map<String, Dimension> LAUNCHER_ICON_SIZES;

    static {
        LAUNCHER_ICON_SIZES = new HashMap<>();
        LAUNCHER_ICON_SIZES.put("mdpi",    new Dimension(48, 48));
        LAUNCHER_ICON_SIZES.put("hdpi",    new Dimension(72, 72));
        LAUNCHER_ICON_SIZES.put("xhdpi",   new Dimension(96, 96));
        LAUNCHER_ICON_SIZES.put("xxhdpi",  new Dimension(144, 144));
        LAUNCHER_ICON_SIZES.put("xxxhdpi", new Dimension(192, 192));
        LAUNCHER_ICON_SIZES.put("ldpi",    new Dimension(36, 36));
    }

    // Expected sizes for action bar icons by density qualifier
    private static final Map<String, Dimension> ACTION_BAR_ICON_SIZES;

    static {
        ACTION_BAR_ICON_SIZES = new HashMap<>();
        ACTION_BAR_ICON_SIZES.put("mdpi",    new Dimension(32, 32));
        ACTION_BAR_ICON_SIZES.put("hdpi",    new Dimension(48, 48));
        ACTION_BAR_ICON_SIZES.put("xhdpi",   new Dimension(64, 64));
        ACTION_BAR_ICON_SIZES.put("xxhdpi",  new Dimension(96, 96));
        ACTION_BAR_ICON_SIZES.put("xxxhdpi", new Dimension(128, 128));
        ACTION_BAR_ICON_SIZES.put("ldpi",    new Dimension(24, 24));
    }

    // Expected sizes for notification icons by density qualifier
    private static final Map<String, Dimension> NOTIFICATION_ICON_SIZES;

    static {
        NOTIFICATION_ICON_SIZES = new HashMap<>();
        NOTIFICATION_ICON_SIZES.put("mdpi",    new Dimension(24, 24));
        NOTIFICATION_ICON_SIZES.put("hdpi",    new Dimension(36, 36));
        NOTIFICATION_ICON_SIZES.put("xhdpi",   new Dimension(48, 48));
        NOTIFICATION_ICON_SIZES.put("xxhdpi",  new Dimension(72, 72));
        NOTIFICATION_ICON_SIZES.put("xxxhdpi", new Dimension(96, 96));
        NOTIFICATION_ICON_SIZES.put("ldpi",    new Dimension(18, 18));
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

        // Only check image files
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".webp")
                && !lowerName.endsWith(".gif") && !lowerName.endsWith(".jpg")
                && !lowerName.endsWith(".jpeg")) {
            return;
        }

        // Determine the folder name to extract density
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderName = folder.getName();

        // Extract density from folder name (e.g., "drawable-hdpi" -> "hdpi")
        String density = getDensity(folderName);
        if (density == null) {
            return;
        }

        // Determine icon type from file name
        String baseName = getBaseName(fileName);
        Map<String, Dimension> expectedSizes = null;
        String iconType = null;

        if (isLauncherIcon(baseName)) {
            expectedSizes = LAUNCHER_ICON_SIZES;
            iconType = "launcher";
        } else if (isActionBarIcon(baseName)) {
            expectedSizes = ACTION_BAR_ICON_SIZES;
            iconType = "action bar";
        } else if (isNotificationIcon(baseName)) {
            expectedSizes = NOTIFICATION_ICON_SIZES;
            iconType = "notification";
        }

        if (expectedSizes == null || iconType == null) {
            return;
        }

        Dimension expected = expectedSizes.get(density);
        if (expected == null) {
            return;
        }

        // Read actual image dimensions
        Dimension actual = getImageDimension(file);
        if (actual == null) {
            return;
        }

        if (actual.width != expected.width || actual.height != expected.height) {
            String message = String.format(
                    "Incorrect icon size for %s icon in %s: expected %dx%d, but was %dx%d",
                    iconType,
                    folderName,
                    expected.width,
                    expected.height,
                    actual.width,
                    actual.height
            );
            Location location = Location.create(file);
            context.report(ICON_EXPECTED_SIZE, location, message);
        }
    }

    /**
     * Extracts the density qualifier from a resource folder name.
     * E.g., "drawable-hdpi" -> "hdpi", "mipmap-xxxhdpi" -> "xxxhdpi"
     */
    private static String getDensity(@NonNull String folderName) {
        String[] densities = {"xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi"};
        for (String density : densities) {
            if (folderName.contains(density)) {
                return density;
            }
        }
        return null;
    }

    /**
     * Returns the base name of a file (without extension).
     */
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    /**
     * Returns true if the icon name suggests it is a launcher icon.
     */
    private static boolean isLauncherIcon(@NonNull String baseName) {
        String lower = baseName.toLowerCase();
        return lower.equals("ic_launcher")
                || lower.equals("ic_launcher_round")
                || lower.startsWith("ic_launcher_");
    }

    /**
     * Returns true if the icon name suggests it is an action bar icon.
     */
    private static boolean isActionBarIcon(@NonNull String baseName) {
        String lower = baseName.toLowerCase();
        return lower.startsWith("ic_action_")
                || lower.startsWith("ic_menu_");
    }

    /**
     * Returns true if the icon name suggests it is a notification icon.
     */
    private static boolean isNotificationIcon(@NonNull String baseName) {
        String lower = baseName.toLowerCase();
        return lower.startsWith("ic_stat_")
                || lower.startsWith("ic_notification_");
    }

    /**
     * Reads the width and height of an image file.
     */
    private static Dimension getImageDimension(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            // Ignore; can't read image
        }
        return null;
    }
}