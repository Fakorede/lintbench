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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;

import org.w3c.dom.Element;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.imageio.ImageIO;

/**
 * Checks for icons that do not match the expected size for their density bucket.
 */
public class IconDetector extends ResourceXmlDetector {

    /** Expected sizes for launcher icons by density */
    private static final int[][] LAUNCHER_ICON_SIZES = {
        // { density dpi, expected width/height in px }
        { Density.LOW.getDpiValue(),    36  },
        { Density.MEDIUM.getDpiValue(), 48  },
        { Density.HIGH.getDpiValue(),   72  },
        { Density.XHIGH.getDpiValue(),  96  },
        { Density.XXHIGH.getDpiValue(), 144 },
        { Density.XXXHIGH.getDpiValue(),192 },
    };

    /** The main issue: launcher icon has wrong size */
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList(); // We handle binary files, not XML elements
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();

        // Only check image files
        if (!isImageFile(fileName)) {
            return;
        }

        // Determine if this is a launcher icon by checking the file name
        // and the folder it is in.
        String folderName = file.getParentFile() != null
                ? file.getParentFile().getName() : "";

        // Check density from folder name
        Density density = getDensityFromFolder(folderName);
        if (density == null) {
            return;
        }

        // Determine expected size for launcher icons
        int expectedSize = getExpectedLauncherIconSize(density);
        if (expectedSize <= 0) {
            return;
        }

        // Only check files that look like launcher icons
        if (!isLauncherIcon(fileName, folderName)) {
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
                        "Incorrect icon size for %1$s: expected %2$dx%2$d, but was %3$dx%4$d",
                        folderName + "/" + fileName,
                        expectedSize,
                        width,
                        height);
                context.report(ICON_EXPECTED_SIZE,
                        Location.create(file),
                        message);
            }
        } catch (IOException e) {
            // Could not read the image; skip
        }
    }

    /**
     * Returns true if the given file name represents an image file.
     */
    private static boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    /**
     * Returns true if the given file looks like a launcher icon.
     */
    private static boolean isLauncherIcon(@NonNull String fileName,
            @NonNull String folderName) {
        String lower = fileName.toLowerCase();
        // Common launcher icon naming conventions
        if (lower.startsWith("ic_launcher")
                || lower.startsWith("launcher_icon")
                || lower.equals("icon.png")
                || lower.equals("icon.jpg")
                || lower.equals("icon.jpeg")
                || lower.equals("icon.webp")) {
            return true;
        }

        // Also check mipmap folders – typically all icons in mipmap are launcher icons
        if (folderName.startsWith("mipmap")) {
            return true;
        }

        return false;
    }

    /**
     * Returns the {@link Density} corresponding to the given resource folder name,
     * or {@code null} if it cannot be determined.
     */
    private static Density getDensityFromFolder(@NonNull String folderName) {
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
        } else if (folderName.equals("drawable")
                || folderName.equals("mipmap")) {
            // Default density folder – treat as mdpi
            return Density.MEDIUM;
        }
        return null;
    }

    /**
     * Returns the expected launcher icon size (in pixels) for the given density,
     * or -1 if unknown.
     */
    private static int getExpectedLauncherIconSize(@NonNull Density density) {
        int dpi = density.getDpiValue();
        for (int[] entry : LAUNCHER_ICON_SIZES) {
            if (entry[0] == dpi) {
                return entry[1];
            }
        }
        return -1;
    }
}