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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Checks for icons that do not follow the recommended size conventions.
 */
public class IconDetector extends Detector implements Detector.ResourceFileScanner {

    /** The main issue discovered by this detector */
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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)));

    /**
     * Expected sizes for launcher icons (in dp) at each density.
     * The standard launcher icon size is 48dp, which translates to:
     *   ldpi:    36x36
     *   mdpi:    48x48
     *   hdpi:    72x72
     *   xhdpi:   96x96
     *   xxhdpi:  144x144
     *   xxxhdpi: 192x192
     */
    private static final Map<String, Dimension> EXPECTED_SIZES;

    static {
        EXPECTED_SIZES = new HashMap<>();
        EXPECTED_SIZES.put("drawable-ldpi",    new Dimension(36,  36));
        EXPECTED_SIZES.put("drawable-mdpi",    new Dimension(48,  48));
        EXPECTED_SIZES.put("drawable-hdpi",    new Dimension(72,  72));
        EXPECTED_SIZES.put("drawable-xhdpi",   new Dimension(96,  96));
        EXPECTED_SIZES.put("drawable-xxhdpi",  new Dimension(144, 144));
        EXPECTED_SIZES.put("drawable-xxxhdpi", new Dimension(192, 192));
    }

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        // Nothing to do here
    }

    @Override
    public Collection<String> getApplicableFiles() {
        return Collections.singletonList("ic_launcher");
    }

    @Override
    public void visitFile(@NonNull ResourceContext context, @NonNull File file) {
        String fileName = file.getName();

        // Only check image files
        if (!isImageFile(fileName)) {
            return;
        }

        // Get the parent folder name (e.g., "drawable-hdpi")
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }
        String folderName = parentFolder.getName();

        // Check if this is a launcher icon
        String baseName = getBaseName(fileName);
        if (!"ic_launcher".equals(baseName)) {
            return;
        }

        // Look up expected size for this density folder
        Dimension expectedSize = EXPECTED_SIZES.get(folderName);
        if (expectedSize == null) {
            // Not a density-specific folder we know about
            return;
        }

        // Read the actual image dimensions
        Dimension actualSize = getImageSize(file);
        if (actualSize == null) {
            return;
        }

        // Compare actual vs expected
        if (actualSize.width != expectedSize.width || actualSize.height != expectedSize.height) {
            String message = String.format(
                    "Incorrect icon size for %1$s: expected %2$dx%3$d, but was %4$dx%5$d",
                    folderName + "/" + fileName,
                    expectedSize.width,
                    expectedSize.height,
                    actualSize.width,
                    actualSize.height);
            context.report(ICON_EXPECTED_SIZE, Location.create(file), message);
        }
    }

    /**
     * Returns whether the given file name represents an image file.
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
     * Returns the base name of a file (without extension).
     */
    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    /**
     * Reads and returns the dimensions of the given image file,
     * or {@code null} if the image could not be read.
     */
    private static Dimension getImageSize(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            // Could not read image; ignore
        }
        return null;
    }
}