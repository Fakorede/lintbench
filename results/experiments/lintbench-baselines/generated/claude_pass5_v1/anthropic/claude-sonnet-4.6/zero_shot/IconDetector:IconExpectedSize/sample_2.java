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
import java.util.EnumSet;

import javax.imageio.ImageIO;

/**
 * Checks for icons that do not follow the expected size conventions for launcher icons.
 */
public class IconDetector extends com.android.tools.lint.detector.api.ResourceXmlDetector
        implements com.android.tools.lint.detector.api.Detector.OtherFileScanner {

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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)));

    // Expected sizes for launcher icons at each density (width x height in dp equivalent)
    // mdpi: 48x48, hdpi: 72x72, xhdpi: 96x96, xxhdpi: 144x144, xxxhdpi: 192x192
    private static final int MDPI_SIZE    = 48;
    private static final int HDPI_SIZE    = 72;
    private static final int XHDPI_SIZE   = 96;
    private static final int XXHDPI_SIZE  = 144;
    private static final int XXXHDPI_SIZE = 192;

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    @NonNull
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull String folderName) {
        // handled via visitFile
    }

    @NonNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.ALL_RESOURCE_FILES);
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        String fileName = file.getName();

        // Only check image files
        if (!isImageFile(fileName)) {
            return;
        }

        // Only check launcher icons (ic_launcher*)
        if (!fileName.startsWith("ic_launcher")) {
            return;
        }

        // Get the parent directory name to determine density
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }
        String parentName = parentDir.getName();

        // Determine expected size based on density qualifier
        int expectedSize = getExpectedSize(parentName);
        if (expectedSize <= 0) {
            return;
        }

        // Read the image and check its dimensions
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Launcher icons for %s should be %dx%d, but this icon is %dx%d",
                        parentName, expectedSize, expectedSize, width, height);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException e) {
            // Can't read the image; skip it
        }
    }

    /**
     * Returns the expected launcher icon size (in pixels) for the given drawable folder name,
     * or -1 if the folder name doesn't correspond to a known density.
     */
    private static int getExpectedSize(String folderName) {
        if (folderName.equals("drawable-mdpi") || folderName.equals("drawable")) {
            return MDPI_SIZE;
        } else if (folderName.equals("drawable-hdpi")) {
            return HDPI_SIZE;
        } else if (folderName.equals("drawable-xhdpi")) {
            return XHDPI_SIZE;
        } else if (folderName.equals("drawable-xxhdpi")) {
            return XXHDPI_SIZE;
        } else if (folderName.equals("drawable-xxxhdpi")) {
            return XXXHDPI_SIZE;
        } else if (folderName.equals("drawable-ldpi")) {
            // ldpi: 36x36
            return 36;
        }
        return -1;
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
     * Returns the density for the given drawable folder name, or null if not recognized.
     */
    private static Density getDensity(String folderName) {
        if (folderName.equals("drawable-ldpi")) {
            return Density.LOW;
        } else if (folderName.equals("drawable-mdpi") || folderName.equals("drawable")) {
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