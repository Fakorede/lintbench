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
import com.android.tools.lint.detector.api.ResourceFolderScanner;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

/**
 * Checks for WebP images used in projects that target API levels that don't support WebP.
 */
public class IconDetector extends ResourceFolderScanner {

    /** WebP format used but not supported */
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // Handled in checkBinaryResource
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        // Basic WebP support requires API 15
        if (minSdk < 15) {
            String message = "WebP requires API 15 (current min is " + minSdk + ")";
            Location location = Location.create(file);
            context.report(WEBP_UNSUPPORTED, location, message);
            return;
        }

        // Lossless and transparency require API 18
        if (minSdk < 18) {
            // Check if the WebP file uses lossless encoding or transparency
            if (isLosslessOrTransparentWebp(file)) {
                String message = "Lossless WebP and WebP with transparency require API 18 " +
                        "(current min is " + minSdk + ")";
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    /**
     * Checks whether the given WebP file uses lossless encoding or has an alpha channel
     * (transparency), which requires API 18+.
     *
     * WebP file format:
     * - Bytes 0-3: "RIFF"
     * - Bytes 4-7: file size (little-endian)
     * - Bytes 8-11: "WEBP"
     * - Bytes 12-15: chunk FourCC (e.g., "VP8 ", "VP8L", "VP8X")
     *
     * VP8L = lossless
     * VP8X = extended format (may have alpha)
     * VP8  = lossy (no alpha)
     */
    private static boolean isLosslessOrTransparentWebp(@NonNull File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[21];
            int read = readFully(is, header, 0, header.length);
            if (read < 16) {
                return false;
            }

            // Verify RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            // Verify WEBP marker
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            // Check chunk type
            char c0 = (char) header[12];
            char c1 = (char) header[13];
            char c2 = (char) header[14];
            char c3 = (char) header[15];

            // VP8L = lossless WebP
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                return true;
            }

            // VP8X = extended WebP, which may have alpha channel
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                // The VP8X chunk flags byte is at offset 20 (after 4 bytes FourCC + 4 bytes size
                // + 4 bytes reserved + 4 bytes flags... actually let's check the spec)
                // VP8X chunk layout:
                //   4 bytes: "VP8X"
                //   4 bytes: chunk size (little-endian) = 10
                //   4 bytes: flags (bit 4 = alpha/transparency present, bit 1 = animation)
                //   3 bytes: canvas width minus one
                //   3 bytes: canvas height minus one
                // So flags are at offset 12 + 4 (FourCC) + 4 (size) = 20
                if (read >= 21) {
                    int flags = header[20] & 0xFF;
                    // Bit 4 (0x10) indicates alpha channel present
                    // Bit 1 (0x02) indicates animation present
                    boolean hasAlpha = (flags & 0x10) != 0;
                    boolean hasAnimation = (flags & 0x02) != 0;
                    if (hasAlpha || hasAnimation) {
                        return true;
                    }
                }
                // Even without the alpha flag, VP8X could contain a VP8L chunk (lossless)
                // For safety, check if there's a VP8L chunk within the extended container
                return containsLosslessChunk(file);
            }

            // VP8  = lossy, no alpha - this is fine for API 15+
            return false;

        } catch (IOException e) {
            // If we can't read the file, conservatively assume it might need API 18
            return false;
        }
    }

    /**
     * Checks if a VP8X WebP file contains a VP8L (lossless) chunk.
     */
    private static boolean containsLosslessChunk(@NonNull File file) {
        try (InputStream is = new FileInputStream(file)) {
            // Skip the RIFF header (12 bytes) and the VP8X chunk (4+4+10 = 18 bytes)
            long skipped = is.skip(30);
            if (skipped < 30) {
                return false;
            }

            // Read remaining chunks
            byte[] chunkHeader = new byte[8];
            while (true) {
                int read = readFully(is, chunkHeader, 0, 8);
                if (read < 8) {
                    break;
                }

                char c0 = (char) chunkHeader[0];
                char c1 = (char) chunkHeader[1];
                char c2 = (char) chunkHeader[2];
                char c3 = (char) chunkHeader[3];

                if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                    return true;
                }

                // Get chunk size (little-endian) and skip chunk data
                int chunkSize = (chunkHeader[4] & 0xFF)
                        | ((chunkHeader[5] & 0xFF) << 8)
                        | ((chunkHeader[6] & 0xFF) << 16)
                        | ((chunkHeader[7] & 0xFF) << 24);

                // Chunks are padded to even sizes
                int paddedSize = (chunkSize + 1) & ~1;

                long totalSkipped = 0;
                while (totalSkipped < paddedSize) {
                    long s = is.skip(paddedSize - totalSkipped);
                    if (s <= 0) {
                        break;
                    }
                    totalSkipped += s;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /**
     * Reads exactly {@code len} bytes from the input stream into the buffer.
     *
     * @return the number of bytes actually read
     */
    private static int readFully(@NonNull InputStream is, @NonNull byte[] buffer,
            int offset, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = is.read(buffer, offset + total, len - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }
}