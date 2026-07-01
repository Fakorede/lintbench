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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.imageio.ImageIO;

/**
 * Checks for icons that do not follow the recommended sizes for launcher icons.
 */
public class IconDetector extends com.android.tools.lint.detector.api.Detector
        implements ResourceFolderScanner {

    /** The main issue discovered by this detector */
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
                    Scope.RESOURCE_FOLDER_SCOPE));

    // Expected sizes for launcher icons by density
    // Based on Android design guidelines:
    // ldpi:    36x36
    // mdpi:    48x48
    // hdpi:    72x72
    // xhdpi:   96x96
    // xxhdpi:  144x144
    // xxxhdpi: 192x192

    private static final int LDPI_SIZE    = 36;
    private static final int MDPI_SIZE    = 48;
    private static final int HDPI_SIZE    = 72;
    private static final int XHDPI_SIZE   = 96;
    private static final int XXHDPI_SIZE  = 144;
    private static final int XXXHDPI_SIZE = 192;

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull String folderName) {
        // Determine if this is a drawable folder
        if (!folderName.startsWith("drawable") && !folderName.startsWith("mipmap")) {
            return;
        }

        // Determine the density from the folder name
        Density density = getDensity(folderName);
        if (density == null) {
            return;
        }

        // Get the expected size for launcher icons at this density
        int expectedSize = getExpectedLauncherIconSize(density);
        if (expectedSize <= 0) {
            return;
        }

        // Get the folder
        File folder = context.file;
        if (!folder.isDirectory()) {
            return;
        }

        // Check each file in the folder
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            if (!isImageFile(name)) {
                continue;
            }

            // Only check launcher icons (ic_launcher*)
            if (!isLauncherIcon(name)) {
                continue;
            }

            checkIconSize(context, file, expectedSize);
        }
    }

    private void checkIconSize(@NonNull Context context, @NonNull File file, int expectedSize) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Incorrect icon size for `%1$s`: expected %2$dx%2$d, but was %3$dx%4$d",
                        file.getName(), expectedSize, width, height);
                context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(file),
                        message);
            }
        } catch (IOException e) {
            // Can't read the image; skip it
        }
    }

    private static boolean isLauncherIcon(@NonNull String name) {
        // Common launcher icon names
        String lowerName = name.toLowerCase();
        return lowerName.startsWith("ic_launcher") ||
               lowerName.equals("icon.png") ||
               lowerName.equals("icon.jpg") ||
               lowerName.equals("icon.gif") ||
               lowerName.equals("icon.webp");
    }

    private static boolean isImageFile(@NonNull String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".png") ||
               lowerName.endsWith(".jpg") ||
               lowerName.endsWith(".jpeg") ||
               lowerName.endsWith(".gif") ||
               lowerName.endsWith(".webp");
    }

    private static Density getDensity(@NonNull String folderName) {
        if (folderName.contains("-ldpi")) {
            return Density.LOW;
        } else if (folderName.contains("-mdpi")) {
            return Density.MEDIUM;
        } else if (folderName.contains("-hdpi")) {
            return Density.HIGH;
        } else if (folderName.contains("-xhdpi")) {
            return Density.XHIGH;
        } else if (folderName.contains("-xxhdpi")) {
            return Density.XXHIGH;
        } else if (folderName.contains("-xxxhdpi")) {
            return Density.XXXHIGH;
        } else if (folderName.equals("drawable") || folderName.equals("mipmap")) {
            // No density qualifier - treat as mdpi
            return Density.MEDIUM;
        }
        return null;
    }

    private static int getExpectedLauncherIconSize(@NonNull Density density) {
        switch (density) {
            case LOW:     return LDPI_SIZE;
            case MEDIUM:  return MDPI_SIZE;
            case HIGH:    return HDPI_SIZE;
            case XHIGH:   return XHDPI_SIZE;
            case XXHIGH:  return XXHDPI_SIZE;
            case XXXHIGH: return XXXHDPI_SIZE;
            default:      return -1;
        }
    }

    @Override
    public Collection<Scope> getApplicableFiles() {
        return Collections.singletonList(Scope.RESOURCE_FOLDER);
    }
}