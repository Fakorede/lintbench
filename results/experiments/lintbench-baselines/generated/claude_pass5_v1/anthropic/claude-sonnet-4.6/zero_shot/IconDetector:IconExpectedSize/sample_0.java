/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Checks for icons that have incorrect sizes according to the Android design guidelines.
 */
public class IconDetector extends com.android.tools.lint.detector.api.ResourceFileDetector {

    /** Expected sizes for launcher icons by density folder name */
    private static final Map<String, Dimension> LAUNCHER_ICON_SIZES;

    /** Expected sizes for action bar / menu icons by density folder name */
    private static final Map<String, Dimension> ACTION_BAR_ICON_SIZES;

    /** Expected sizes for notification icons by density folder name */
    private static final Map<String, Dimension> NOTIFICATION_ICON_SIZES;

    /** Expected sizes for small / contextual icons by density folder name */
    private static final Map<String, Dimension> SMALL_ICON_SIZES;

    static {
        LAUNCHER_ICON_SIZES = new HashMap<>();
        LAUNCHER_ICON_SIZES.put("drawable-ldpi",   new Dimension(36,  36));
        LAUNCHER_ICON_SIZES.put("drawable-mdpi",   new Dimension(48,  48));
        LAUNCHER_ICON_SIZES.put("drawable-hdpi",   new Dimension(72,  72));
        LAUNCHER_ICON_SIZES.put("drawable-xhdpi",  new Dimension(96,  96));
        LAUNCHER_ICON_SIZES.put("drawable-xxhdpi", new Dimension(144, 144));
        LAUNCHER_ICON_SIZES.put("drawable-xxxhdpi",new Dimension(192, 192));

        ACTION_BAR_ICON_SIZES = new HashMap<>();
        ACTION_BAR_ICON_SIZES.put("drawable-ldpi",   new Dimension(18, 18));
        ACTION_BAR_ICON_SIZES.put("drawable-mdpi",   new Dimension(24, 24));
        ACTION_BAR_ICON_SIZES.put("drawable-hdpi",   new Dimension(36, 36));
        ACTION_BAR_ICON_SIZES.put("drawable-xhdpi",  new Dimension(48, 48));
        ACTION_BAR_ICON_SIZES.put("drawable-xxhdpi", new Dimension(72, 72));

        NOTIFICATION_ICON_SIZES = new HashMap<>();
        NOTIFICATION_ICON_SIZES.put("drawable-ldpi",   new Dimension(18, 18));
        NOTIFICATION_ICON_SIZES.put("drawable-mdpi",   new Dimension(24, 24));
        NOTIFICATION_ICON_SIZES.put("drawable-hdpi",   new Dimension(36, 36));
        NOTIFICATION_ICON_SIZES.put("drawable-xhdpi",  new Dimension(48, 48));
        NOTIFICATION_ICON_SIZES.put("drawable-xxhdpi", new Dimension(72, 72));

        SMALL_ICON_SIZES = new HashMap<>();
        SMALL_ICON_SIZES.put("drawable-ldpi",   new Dimension(16, 16));
        SMALL_ICON_SIZES.put("drawable-mdpi",   new Dimension(16, 16));
        SMALL_ICON_SIZES.put("drawable-hdpi",   new Dimension(24, 24));
        SMALL_ICON_SIZES.put("drawable-xhdpi",  new Dimension(32, 32));
        SMALL_ICON_SIZES.put("drawable-xxhdpi", new Dimension(48, 48));
    }

    /** The main issue */
    public static final Issue ISSUE = Issue.create(
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        String fileName = file.getName();

        // Only process image files
        if (!isImageFile(fileName)) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();

        // Determine icon type based on file name patterns
        IconType iconType = getIconType(fileName);
        if (iconType == IconType.UNKNOWN) {
            return;
        }

        Map<String, Dimension> expectedSizes = getExpectedSizes(iconType);
        if (expectedSizes == null) {
            return;
        }

        Dimension expectedSize = expectedSizes.get(folderName);
        if (expectedSize == null) {
            // We don't have expected sizes for this density folder
            return;
        }

        // Read the actual image dimensions
        Dimension actualSize = getImageDimension(file);
        if (actualSize == null) {
            return;
        }

        if (actualSize.width != expectedSize.width || actualSize.height != expectedSize.height) {
            String message = String.format(
                    "Incorrect icon size for %1$s: expected %2$dx%3$d, but was %4$dx%5$d",
                    folderName + "/" + fileName,
                    expectedSize.width, expectedSize.height,
                    actualSize.width, actualSize.height);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    /**
     * Returns the image dimensions of the given file, or null if the dimensions
     * cannot be determined.
     */
    private static Dimension getImageDimension(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            // Can't read image; ignore
        }
        return null;
    }

    /**
     * Returns true if the given file name looks like an image file.
     */
    private static boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    /**
     * Represents the type of icon.
     */
    private enum IconType {
        LAUNCHER,
        ACTION_BAR,
        NOTIFICATION,
        SMALL,
        UNKNOWN
    }

    /**
     * Determines the icon type based on the file name.
     */
    private static IconType getIconType(String fileName) {
        String lower = fileName.toLowerCase();

        // Launcher icons typically named ic_launcher*
        if (lower.startsWith("ic_launcher")) {
            return IconType.LAUNCHER;
        }

        // Action bar / menu icons
        if (lower.startsWith("ic_menu_") || lower.startsWith("ic_action_")) {
            return IconType.ACTION_BAR;
        }

        // Notification icons
        if (lower.startsWith("ic_stat_") || lower.startsWith("ic_notification_")) {
            return IconType.NOTIFICATION;
        }

        // Small / contextual icons
        if (lower.startsWith("ic_small_") || lower.startsWith("ic_list_")) {
            return IconType.SMALL;
        }

        return IconType.UNKNOWN;
    }

    /**
     * Returns the expected sizes map for the given icon type.
     */
    private static Map<String, Dimension> getExpectedSizes(IconType iconType) {
        switch (iconType) {
            case LAUNCHER:
                return LAUNCHER_ICON_SIZES;
            case ACTION_BAR:
                return ACTION_BAR_ICON_SIZES;
            case NOTIFICATION:
                return NOTIFICATION_ICON_SIZES;
            case SMALL:
                return SMALL_ICON_SIZES;
            default:
                return null;
        }
    }

    /**
     * Returns the density for the given drawable folder name, or null if not a density-specific
     * folder.
     */
    private static Density getDensity(String folderName) {
        if (folderName.equals("drawable-ldpi")) {
            return Density.LOW;
        } else if (folderName.equals("drawable-mdpi")) {
            return Density.MEDIUM;
        } else if (folderName.equals("drawable-hdpi")) {
            return Density.HIGH;
        } else if (folderName.equals("drawable-xhdpi")) {
            return Density.XHIGH;
        } else if (folderName.equals("drawable-xxhdpi")) {
            return Density.XXHIGH;
        } else if (folderName.equals("drawable-xxxhdpi")) {
            return Density.XXXHIGH;
        }
        return null;
    }
}