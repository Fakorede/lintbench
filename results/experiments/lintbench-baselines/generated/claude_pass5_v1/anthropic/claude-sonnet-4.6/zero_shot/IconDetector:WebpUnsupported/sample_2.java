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
import java.io.InputStream;
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
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    @Override
    public Collection<String> appliesTo(@NonNull ResourceFolderType folderType) {
        return Collections.singletonList("drawable");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return folderType == ResourceFolderType.DRAWABLE ||
               folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<Issue> getIssues() {
        return Collections.singletonList(WEBP_UNSUPPORTED);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();

        // Check if the WebP file uses lossless or transparency features
        boolean usesLosslessOrTransparency = false;
        try {
            usesLosslessOrTransparency = webpUsesLosslessOrTransparency(file);
        } catch (IOException e) {
            // If we can't read the file, assume worst case
            usesLosslessOrTransparency = false;
        }

        if (usesLosslessOrTransparency) {
            // Lossless/transparency requires API 18
            if (minSdk < 18) {
                Location location = Location.create(file);
                String message;
                if (minSdk < 15) {
                    message = "WebP requires API 15, and lossless/transparent WebP requires API 18, " +
                              "and the current min SDK version is " + minSdk;
                } else {
                    message = "Lossless and transparent WebP requires API 18 (current min is " +
                              minSdk + ")";
                }
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        } else {
            // Basic WebP requires API 15
            if (minSdk < 15) {
                Location location = Location.create(file);
                String message = "WebP requires API 15 (current min is " + minSdk + ")";
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    /**
     * Checks whether the given WebP file uses lossless encoding or transparency,
     * which requires API 18 (Android 4.2.1).
     *
     * WebP file format:
     * - Bytes 0-3: "RIFF"
     * - Bytes 4-7: file size
     * - Bytes 8-11: "WEBP"
     * - Bytes 12-15: chunk FourCC (e.g., "VP8 ", "VP8L", "VP8X")
     *
     * VP8L = lossless
     * VP8X = extended format (may include transparency/alpha)
     * VP8  = lossy (basic)
     */
    private static boolean webpUsesLosslessOrTransparency(@NonNull File file) throws IOException {
        byte[] header = new byte[16];
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            int read = is.read(header);
            if (read < 16) {
                return false;
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {
                }
            }
        }

        // Check RIFF header
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
            return false;
        }

        // Check WEBP signature
        if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return false;
        }

        // Check chunk type
        // VP8L = lossless encoding
        if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'L') {
            return true;
        }

        // VP8X = extended format, which can have alpha/transparency
        if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X') {
            // Read the VP8X flags to check for alpha
            // VP8X chunk: 4 bytes fourcc, 4 bytes chunk size, 4 bytes flags
            byte[] extHeader = new byte[4];
            InputStream is2 = null;
            try {
                is2 = new FileInputStream(file);
                long skipped = is2.skip(20); // Skip RIFF header (12) + VP8X fourcc (4) + chunk size (4)
                if (skipped < 20) {
                    return false;
                }
                int read = is2.read(extHeader);
                if (read < 4) {
                    return false;
                }
            } finally {
                if (is2 != null) {
                    try {
                        is2.close();
                    } catch (IOException ignore) {
                    }
                }
            }
            // Alpha flag is bit 4 (0x10) of the first flags byte
            int flags = extHeader[0] & 0xFF;
            if ((flags & 0x10) != 0) {
                return true;
            }
        }

        return false;
    }
}