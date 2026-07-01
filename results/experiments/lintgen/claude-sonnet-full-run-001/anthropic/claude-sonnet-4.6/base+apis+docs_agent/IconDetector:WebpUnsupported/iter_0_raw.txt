package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class IconDetector extends Detector implements BinaryResourceScanner {

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
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    /** Minimum API for basic WebP support */
    private static final int WEBP_BASIC_API = 15;

    /** Minimum API for lossless/transparent WebP support */
    private static final int WEBP_EXTENDED_API = 18;

    public IconDetector() {
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        if (minSdk >= WEBP_EXTENDED_API) {
            // All WebP features are supported; no issue.
            return;
        }

        // Read the WebP file header to determine if it uses lossless or transparency features.
        boolean isLosslessOrTransparent = isLosslessOrTransparentWebp(file);

        if (isLosslessOrTransparent) {
            if (minSdk < WEBP_EXTENDED_API) {
                context.report(
                        WEBP_UNSUPPORTED,
                        context.getLocation(file),
                        "WebP with lossless encoding or transparency requires API 18 " +
                        "(current minSdk is " + minSdk + ")"
                );
            }
        } else {
            // Basic (lossy) WebP
            if (minSdk < WEBP_BASIC_API) {
                context.report(
                        WEBP_UNSUPPORTED,
                        context.getLocation(file),
                        "WebP requires API 15 " +
                        "(current minSdk is " + minSdk + ")"
                );
            }
        }
    }

    /**
     * Checks whether the given WebP file uses lossless encoding or transparency,
     * which requires API 18+.
     *
     * WebP file structure:
     *   Bytes 0-3:  "RIFF"
     *   Bytes 4-7:  file size (little-endian)
     *   Bytes 8-11: "WEBP"
     *   Bytes 12-15: chunk FourCC (e.g., "VP8 ", "VP8L", "VP8X")
     *
     * VP8L = lossless (requires API 18)
     * VP8X = extended format, may include transparency (requires API 18)
     * VP8  = lossy (requires API 15)
     */
    private static boolean isLosslessOrTransparentWebp(@NonNull File file) {
        try {
            byte[] header = new byte[21];
            try (InputStream is = Files.newInputStream(file.toPath())) {
                int read = 0;
                while (read < header.length) {
                    int n = is.read(header, read, header.length - read);
                    if (n < 0) break;
                    read += n;
                }
                if (read < 16) {
                    return false;
                }
            }

            // Verify RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }
            // Verify WEBP marker
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            // Check chunk type at bytes 12-15
            char c0 = (char) header[12];
            char c1 = (char) header[13];
            char c2 = (char) header[14];
            char c3 = (char) header[15];

            // VP8L = lossless
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                return true;
            }

            // VP8X = extended; check alpha flag (bit 4 of flags byte at offset 20)
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                // Extended WebP requires API 18 regardless of alpha
                return true;
            }

            // VP8 (lossy) = basic WebP, API 15+
            return false;

        } catch (IOException e) {
            // If we can't read the file, assume basic WebP
            return false;
        }
    }
}