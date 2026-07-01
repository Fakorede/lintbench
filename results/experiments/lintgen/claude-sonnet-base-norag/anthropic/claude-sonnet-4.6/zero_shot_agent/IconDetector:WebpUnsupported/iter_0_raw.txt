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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    // WebP file signature bytes
    // RIFF????WEBP
    private static final byte[] RIFF_HEADER = new byte[]{'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARKER = new byte[]{'W', 'E', 'B', 'P'};

    // VP8L chunk marker (lossless)
    private static final byte[] VP8L_MARKER = new byte[]{'V', 'P', '8', 'L'};
    // VP8A chunk marker (with alpha/transparency) - actually it's VP8X with alpha flag
    private static final byte[] VP8X_MARKER = new byte[]{'V', 'P', '8', 'X'};

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
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();

        // Check if the WebP file uses lossless or transparency features
        boolean usesLosslessOrTransparency = webpUsesLosslessOrTransparency(file);

        if (usesLosslessOrTransparency) {
            // Lossless/transparency requires API 18
            if (minSdk < 18) {
                String message;
                if (minSdk < 15) {
                    message = "WebP requires API 15, and lossless/transparent WebP requires API 18; " +
                            "current minSdkVersion is " + minSdk;
                } else {
                    message = "Lossless and transparent WebP requires API 18; " +
                            "current minSdkVersion is " + minSdk;
                }
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        } else {
            // Basic WebP requires API 15
            if (minSdk < 15) {
                String message = "WebP requires API 15; current minSdkVersion is " + minSdk;
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    /**
     * Checks whether the given WebP file uses lossless encoding or transparency,
     * which requires API 18.
     */
    private static boolean webpUsesLosslessOrTransparency(@NonNull File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = readFully(is, header, 0, 12);
            if (read < 12) {
                return false;
            }

            // Check RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            // Check WEBP marker at offset 8
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            // Read the chunk type (next 4 bytes)
            byte[] chunkType = new byte[4];
            read = readFully(is, chunkType, 0, 4);
            if (read < 4) {
                return false;
            }

            // VP8L = lossless (requires API 18)
            if (chunkType[0] == 'V' && chunkType[1] == 'P' && chunkType[2] == '8' && chunkType[3] == 'L') {
                return true;
            }

            // VP8X = extended format, may include alpha (requires API 18)
            if (chunkType[0] == 'V' && chunkType[1] == 'P' && chunkType[2] == '8' && chunkType[3] == 'X') {
                // Read chunk size (4 bytes, little-endian)
                byte[] chunkSize = new byte[4];
                read = readFully(is, chunkSize, 0, 4);
                if (read < 4) {
                    return false;
                }

                // Read the flags byte
                byte[] flags = new byte[1];
                read = readFully(is, flags, 0, 1);
                if (read < 1) {
                    return false;
                }

                // Bit 1 (0x02) is the ICC profile flag
                // Bit 4 (0x10) is the alpha flag
                // Bit 5 (0x20) is the Exif metadata flag
                // Bit 6 (0x40) is the XMP metadata flag
                // Bit 1 of flags byte: alpha channel present
                // According to WebP spec, flags byte bits:
                // bit 1: ICC profile
                // bit 2: Alpha
                // bit 3: Exif metadata
                // bit 4: XMP metadata
                // bit 5: Animation
                boolean hasAlpha = (flags[0] & 0x10) != 0;
                return hasAlpha;
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static int readFully(@NonNull InputStream is, @NonNull byte[] buffer, int offset, int length)
            throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = is.read(buffer, offset + totalRead, length - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableFiles() {
        return null;
    }
}