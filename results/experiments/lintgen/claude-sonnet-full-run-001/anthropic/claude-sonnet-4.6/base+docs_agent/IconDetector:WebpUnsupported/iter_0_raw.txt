package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements ResourceFolderScanner {

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
                    EnumSet.of(Scope.RESOURCE_FOLDER)
            )
    );

    public IconDetector() {
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull String folderName) {
        File folder = context.file;
        if (!folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".webp")) {
                if (minSdk < 15) {
                    String message = "WebP requires Android 4.0 (API 15); current minSdkVersion is " + minSdk;
                    context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            message
                    );
                } else if (minSdk < 18) {
                    // Check if the WebP file uses lossless encoding or transparency
                    if (isLosslessOrTransparentWebP(file)) {
                        String message = "Lossless WebP encoding and transparency requires Android 4.2.1 " +
                                "(API 18); current minSdkVersion is " + minSdk;
                        context.report(
                                WEBP_UNSUPPORTED,
                                Location.create(file),
                                message
                        );
                    }
                }
            }
        }
    }

    /**
     * Checks whether a WebP file uses lossless encoding or has transparency (alpha channel).
     * WebP lossless files start with the RIFF header, then "WEBP", then "VP8L" chunk for lossless.
     * Transparent lossy WebP files use "VP8X" chunk with the alpha flag set.
     */
    private boolean isLosslessOrTransparentWebP(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            // WebP file format:
            // Bytes 0-3: "RIFF"
            // Bytes 4-7: file size (little-endian)
            // Bytes 8-11: "WEBP"
            // Bytes 12-15: chunk FourCC (VP8 , VP8L, or VP8X)
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 16) {
                return false;
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
            String chunkType = new String(header, 12, 4, "US-ASCII");

            if ("VP8L".equals(chunkType)) {
                // Lossless WebP
                return true;
            }

            if ("VP8X".equals(chunkType)) {
                // Extended WebP - check flags
                // VP8X chunk data starts at byte 20 (after 8-byte chunk header)
                // Flags byte: bit 4 = animation, bit 3 = ICC, bit 2 = alpha, bit 1 = EXIF, bit 0 = XMP
                byte[] flags = new byte[4];
                read = fis.read(flags);
                if (read < 4) {
                    return false;
                }
                // Alpha flag is bit 4 (0x10) of the first flags byte
                // Actually in VP8X: flags are at chunk data offset 0
                // Bit 1 (0x02) = ICC profile, Bit 2 (0x04) = Alpha, Bit 3 (0x08) = EXIF, Bit 4 (0x10) = XMP, Bit 5 (0x20) = Animation
                int flagByte = flags[0] & 0xFF;
                boolean hasAlpha = (flagByte & 0x10) != 0;
                return hasAlpha;
            }

            // VP8 is lossy without transparency
            return false;

        } catch (IOException e) {
            // If we can't read the file, try using ImageIO to check for alpha
            try {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    return image.getColorModel().hasAlpha();
                }
            } catch (IOException ignored) {
                // ignore
            }
            return false;
        }
    }
}