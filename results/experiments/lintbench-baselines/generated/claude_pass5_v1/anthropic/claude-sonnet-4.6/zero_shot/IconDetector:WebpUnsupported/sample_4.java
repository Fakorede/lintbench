package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.imageio.ImageIO;

public class IconDetector extends com.android.tools.lint.detector.api.ResourceFileDetector {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    public IconDetector() {
    }

    @Override
    public Collection<String> appliesTo(@NonNull ResourceFolderType folderType) {
        return Collections.singletonList(folderType.getName());
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(".webp")) {
            checkWebpFile(context, file);
        }
    }

    private void checkWebpFile(@NonNull Context context, @NonNull File file) {
        int minSdkVersion = context.getMainProject().getMinSdk();

        // Basic WebP support requires API 15
        if (minSdkVersion < 15) {
            Location location = Location.create(file);
            context.report(
                    WEBP_UNSUPPORTED,
                    location,
                    "WebP requires API 15 (minSdkVersion is " + minSdkVersion + ")"
            );
            return;
        }

        // Lossless and transparency features require API 18
        if (minSdkVersion < 18) {
            // Check if the WebP file uses lossless encoding or transparency
            if (isLosslessOrTransparentWebp(file)) {
                Location location = Location.create(file);
                context.report(
                        WEBP_UNSUPPORTED,
                        location,
                        "Lossless WebP and WebP with transparency requires API 18 " +
                        "(minSdkVersion is " + minSdkVersion + ")"
                );
            }
        }
    }

    private boolean isLosslessOrTransparentWebp(@NonNull File file) {
        // Read the WebP file header to determine if it uses lossless encoding or transparency
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[30];
            int bytesRead = fis.read(header);
            if (bytesRead < 12) {
                return false;
            }

            // WebP file format:
            // Bytes 0-3: "RIFF"
            // Bytes 4-7: file size (little-endian)
            // Bytes 8-11: "WEBP"
            // Bytes 12-15: chunk FourCC

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            if (bytesRead < 16) {
                return false;
            }

            // Check the chunk type
            // "VP8L" indicates lossless encoding
            // "VP8X" indicates extended format which may include transparency (alpha)
            char c0 = (char) header[12];
            char c1 = (char) header[13];
            char c2 = (char) header[14];
            char c3 = (char) header[15];

            // VP8L = lossless
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                return true;
            }

            // VP8X = extended; check alpha flag
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                // VP8X chunk data starts at byte 20 (12 + 4 FourCC + 4 chunk size)
                // Flags byte is at offset 20
                // Bit 4 (0x10) indicates alpha channel is present
                if (bytesRead >= 21) {
                    byte flags = header[20];
                    if ((flags & 0x10) != 0) {
                        return true;
                    }
                }
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }
}