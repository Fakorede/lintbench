package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;

import javax.imageio.ImageIO;

import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

import java.util.List;

/**
 * Detector for WebP image format compatibility issues.
 * <p>
 * WebP format requires Android 4.0 (API 15). Lossless encoding and transparency
 * require Android 4.2.1 (API 18).
 */
public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    /** WebP format used but not supported */
    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as " +
            "lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 " +
            "is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        Project project = context.getProject();
        int minSdk = project.getMinSdk();

        // Check basic WebP support: requires API 15
        if (minSdk < 15) {
            String message = "WebP requires Android 4.0 (API 15); current minSdkVersion is " +
                    minSdk;
            Location location = Location.create(file);
            context.report(WEBP_UNSUPPORTED, location, message);
            return;
        }

        // Check for lossless/transparency features: requires API 18
        if (minSdk < 18) {
            // Check if the WebP file uses lossless encoding or transparency
            boolean hasLosslessOrTransparency = isLosslessOrTransparentWebP(file);
            if (hasLosslessOrTransparency) {
                String message = "Lossless WebP encoding and transparency requires Android " +
                        "4.2.1 (API 18); current minSdkVersion is " + minSdk;
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    /**
     * Checks whether a WebP file uses lossless encoding or has transparency.
     * <p>
     * WebP file format:
     * - Bytes 0-3: "RIFF"
     * - Bytes 4-7: file size
     * - Bytes 8-11: "WEBP"
     * - Bytes 12-15: chunk FourCC (VP8 , VP8L, VP8X)
     *   - "VP8 " = lossy
     *   - "VP8L" = lossless
     *   - "VP8X" = extended (may have alpha)
     *
     * @param file the WebP file to check
     * @return true if the file uses lossless encoding or has transparency
     */
    private static boolean isLosslessOrTransparentWebP(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[21];
            int read = fis.read(header);
            if (read < 16) {
                return false;
            }

            // Verify RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            // Verify WEBP magic
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            // Check chunk type
            char c0 = (char) header[12];
            char c1 = (char) header[13];
            char c2 = (char) header[14];
            char c3 = (char) header[15];

            // VP8L = lossless
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                return true;
            }

            // VP8X = extended format, check for alpha flag
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                // VP8X chunk data starts at byte 20 (after 4 bytes FourCC + 4 bytes chunk size)
                // The flags byte is at offset 20
                if (read >= 21) {
                    // Alpha flag is bit 4 (0x10) of the flags byte
                    byte flags = header[20];
                    if ((flags & 0x10) != 0) {
                        return true;
                    }
                }
                return false;
            }

            // VP8 = lossy, no transparency
            return false;

        } catch (IOException e) {
            // If we can't read the file, assume it might be problematic
            return false;
        }
    }
}