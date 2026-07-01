/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_JPEG;
import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collection;
import java.util.Collections;

import javax.imageio.ImageIO;

/**
 * Checks for images that can be converted to WebP format for better compression.
 */
public class IconDetector extends Detector implements Detector.ResourceFileScanner {

    /** The main issue discovered by this detector */
    public static final Issue WEBP_ELIGIBLE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there "
                    + "is a quickfix in the IDE which lets you perform conversion.\n"
                    + "\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            1,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    // ---- Implements Detector.ResourceFileScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void run(@NonNull Context context) {
        // Not used in this detector
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(DOT_PNG)
                || name.endsWith(DOT_JPG)
                || name.endsWith(DOT_JPEG);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String fileName = file.getName();
        String lowerName = fileName.toLowerCase();

        // Skip 9-patch files
        if (lowerName.endsWith(DOT_9PNG)) {
            return;
        }

        // Only process PNG and JPEG files
        boolean isPng = lowerName.endsWith(DOT_PNG);
        boolean isJpeg = lowerName.endsWith(DOT_JPG) || lowerName.endsWith(DOT_JPEG);

        if (!isPng && !isJpeg) {
            return;
        }

        // Skip files that are already WebP
        if (lowerName.endsWith(DOT_WEBP)) {
            return;
        }

        // Check if we're in a resource directory
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();

        // Only flag files in drawable/mipmap directories
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        // For PNG files, check if they have transparency (alpha channel)
        // Lossless WebP supports transparency, so all PNGs are eligible
        // JPEG files are also eligible since WebP can replace them with lossy compression

        if (isPng) {
            // Check minimum SDK version - WebP with transparency requires API 18 (4.2.1)
            // Basic WebP support (lossy, no alpha) requires API 14
            // We'll flag all PNGs and let the IDE quickfix handle the details
            boolean hasAlpha = false;
            try {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    hasAlpha = image.getColorModel().hasAlpha();
                }
            } catch (IOException e) {
                // If we can't read the image, skip it
                return;
            }

            String message;
            if (hasAlpha) {
                message = String.format(
                        "`%1$s` can be converted to WebP (lossless, with transparency) "
                                + "which is typically more compact; use the IDE quickfix to convert",
                        fileName);
            } else {
                message = String.format(
                        "`%1$s` can be converted to WebP "
                                + "which is typically more compact; use the IDE quickfix to convert",
                        fileName);
            }

            Location location = Location.create(file);
            context.report(WEBP_ELIGIBLE, location, message);

        } else if (isJpeg) {
            String message = String.format(
                    "`%1$s` can be converted to WebP "
                            + "which is typically more compact; use the IDE quickfix to convert",
                    fileName);

            Location location = Location.create(file);
            context.report(WEBP_ELIGIBLE, location, message);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do
    }
}