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
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Checks for icons that do not follow the recommended sizes.
 */
public class IconDetector extends com.android.tools.lint.detector.api.ResourceXmlDetector
        implements com.android.tools.lint.detector.api.Detector.OtherFileScanner {

    /** Expected sizes for launcher icons by density */
    private static final Map<String, Dimension> LAUNCHER_ICON_SIZES;

    static {
        LAUNCHER_ICON_SIZES = new HashMap<>();
        LAUNCHER_ICON_SIZES.put("drawable-ldpi",    new Dimension(36, 36));
        LAUNCHER_ICON_SIZES.put("drawable-mdpi",    new Dimension(48, 48));
        LAUNCHER_ICON_SIZES.put("drawable-hdpi",    new Dimension(72, 72));
        LAUNCHER_ICON_SIZES.put("drawable-xhdpi",   new Dimension(96, 96));
        LAUNCHER_ICON_SIZES.put("drawable-xxhdpi",  new Dimension(144, 144));
        LAUNCHER_ICON_SIZES.put("drawable-xxxhdpi", new Dimension(192, 192));
    }

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You " +
            " should follow these conventions to make sure your icons fit in with the " +
            " overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    @NonNull
    public Collection<String> getApplicableFiles() {
        return Collections.emptyList();
    }

    @Override
    public EnumSet<Scope> getApplicableFiles(EnumSet<Scope> scope) {
        return EnumSet.of(Scope.ALL_RESOURCE_FILES);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE ||
               folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void run(@NonNull Context context) {
        // This detector runs on resource files; handled via checkFile
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Check all resource directories for launcher icons
        File resDir = context.getProject().getResourceFolders().isEmpty()
                ? null
                : context.getProject().getResourceFolders().get(0);

        if (resDir == null || !resDir.exists()) {
            return;
        }

        // Collect launcher icon name from the manifest if possible
        String launcherIcon = getLauncherIcon(context);

        File[] folders = resDir.listFiles();
        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            String folderName = folder.getName();
            if (!folder.isDirectory()) {
                continue;
            }

            boolean isDrawable = folderName.startsWith("drawable");
            boolean isMipmap   = folderName.startsWith("mipmap");

            if (!isDrawable && !isMipmap) {
                continue;
            }

            // Determine the density qualifier
            Dimension expectedSize = getExpectedSize(folderName);
            if (expectedSize == null) {
                continue;
            }

            File[] files = folder.listFiles();
            if (files == null) {
                continue;
            }

            for (File file : files) {
                String name = file.getName();
                if (!isImageFile(name)) {
                    continue;
                }

                // Only check launcher icons if we know the name, otherwise check all icons
                String baseName = getBaseName(name);
                if (launcherIcon != null && !baseName.equals(launcherIcon)) {
                    continue;
                }

                checkIconSize(context, file, expectedSize, folderName);
            }
        }
    }

    /**
     * Checks whether the given icon file matches the expected size for its density bucket.
     */
    private void checkIconSize(@NonNull Context context,
                               @NonNull File file,
                               @NonNull Dimension expected,
                               @NonNull String folderName) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int width  = image.getWidth();
            int height = image.getHeight();

            if (width != expected.width || height != expected.height) {
                String message = String.format(
                        "Incorrect icon size for `%1$s`: expected %2$dx%3$d, but was %4$dx%5$d",
                        folderName + File.separator + file.getName(),
                        expected.width, expected.height,
                        width, height);
                context.report(ISSUE,
                        com.android.tools.lint.detector.api.Location.create(file),
                        message);
            }
        } catch (IOException e) {
            // Could not read image; skip
        }
    }

    /**
     * Returns the expected launcher-icon size for the given drawable/mipmap folder name,
     * or {@code null} if the density is unknown / not standard.
     */
    private static Dimension getExpectedSize(@NonNull String folderName) {
        // Normalise mipmap-* to drawable-* for lookup purposes
        String lookup = folderName;
        if (folderName.startsWith("mipmap-")) {
            lookup = "drawable-" + folderName.substring("mipmap-".length());
        } else if (folderName.equals("mipmap")) {
            lookup = "drawable-mdpi"; // treat plain mipmap as mdpi
        } else if (folderName.equals("drawable")) {
            lookup = "drawable-mdpi"; // treat plain drawable as mdpi
        }

        return LAUNCHER_ICON_SIZES.get(lookup);
    }

    /**
     * Tries to obtain the launcher icon name from the project manifest.
     * Returns {@code null} if it cannot be determined.
     */
    private static String getLauncherIcon(@NonNull Context context) {
        // Attempt to read the icon attribute from the manifest via the client
        try {
            com.android.tools.lint.client.api.LintClient client = context.getClient();
            // Use the project's merged manifest if available
            com.android.utils.XmlUtils xmlUtils = null; // not used directly

            // Walk the manifest file to find android:icon
            List<File> manifestFiles = context.getProject().getManifestFiles();
            if (manifestFiles.isEmpty()) {
                return null;
            }

            File manifestFile = manifestFiles.get(0);
            if (!manifestFile.exists()) {
                return null;
            }

            String content = client.readFile(manifestFile);
            if (content == null || content.isEmpty()) {
                return null;
            }

            // Simple string search for android:icon="@drawable/..." or "@mipmap/..."
            int idx = content.indexOf("android:icon=\"");
            if (idx == -1) {
                return null;
            }
            idx += "android:icon=\"".length();
            int end = content.indexOf('"', idx);
            if (end == -1) {
                return null;
            }
            String iconRef = content.substring(idx, end);
            // iconRef is something like @drawable/ic_launcher or @mipmap/ic_launcher
            int slash = iconRef.lastIndexOf('/');
            if (slash != -1) {
                return iconRef.substring(slash + 1);
            }
            return iconRef;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns {@code true} if the file name looks like a bitmap image. */
    private static boolean isImageFile(@NonNull String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    /** Strips the extension from a file name. */
    private static String getBaseName(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }
}