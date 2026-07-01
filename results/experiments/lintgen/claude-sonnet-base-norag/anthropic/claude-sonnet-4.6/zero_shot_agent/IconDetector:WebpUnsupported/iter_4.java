package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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
                    EnumSet.of(Scope.RESOURCE_FILE)
            )
    );

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

        boolean usesLosslessOrTransparency = webpUsesLosslessOrTransparency(file);

        if (usesLosslessOrTransparency) {
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
            if (minSdk < 15) {
                String message = "WebP requires API 15; current minSdkVersion is " + minSdk;
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    private static boolean webpUsesLosslessOrTransparency(@NonNull File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = readFully(is, header, 0, 12);
            if (read < 12) {
                return false;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

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

                // Bit 4 (0x10) is the alpha flag in the VP8X flags byte
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
}