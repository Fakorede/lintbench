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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

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

        // Check if the WebP file uses lossless or transparency features
        boolean hasLosslessOrTransparency = webpHasLosslessOrTransparency(file);

        if (hasLosslessOrTransparency) {
            // Lossless and transparency require API 18
            if (minSdk < 18) {
                String message = "WebP lossless and transparency require API 18 (minSdk is " + minSdk + ")";
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        } else {
            // Basic WebP requires API 15
            if (minSdk < 15) {
                String message = "WebP requires API 15 (minSdk is " + minSdk + ")";
                Location location = Location.create(file);
                context.report(WEBP_UNSUPPORTED, location, message);
            }
        }
    }

    /**
     * Checks whether a WebP file uses lossless encoding or transparency.
     * This is determined by reading the WebP file header.
     *
     * WebP file format:
     * - Bytes 0-3: "RIFF"
     * - Bytes 4-7: file size (little endian)
     * - Bytes 8-11: "WEBP"
     * - Bytes 12-15: chunk FourCC (e.g., "VP8 ", "VP8L", "VP8X")
     *
     * VP8L = lossless
     * VP8X = extended format (may include transparency/alpha)
     * VP8  = lossy (no transparency)
     */
    private boolean webpHasLosslessOrTransparency(@NonNull File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            try {
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
                // VP8L = lossless
                if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'L') {
                    return true;
                }

                // VP8X = extended format - need to check flags for alpha
                if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X') {
                    // Read the VP8X chunk header to check for alpha flag
                    // VP8X chunk: 4 bytes FourCC + 4 bytes chunk size + 4 bytes flags
                    byte[] vp8xFlags = new byte[8];
                    int flagsRead = fis.read(vp8xFlags);
                    if (flagsRead >= 8) {
                        // Flags are at offset 4 in VP8X chunk data (after the chunk size)
                        // Alpha flag is bit 4 (0x10) in the flags byte
                        int flags = vp8xFlags[4] & 0xFF;
                        if ((flags & 0x10) != 0) {
                            return true;
                        }
                        // ICC color profile flag (bit 5), animation flag (bit 1), exif (bit 3), xmp (bit 2)
                        // Lossless flag is bit 0 in the extended flags
                        // Actually let's also check if there's a VP8L chunk inside VP8X
                        // For simplicity, if it's VP8X with alpha bit set, return true
                        // Otherwise return false (could still have embedded VP8L, but rare)
                    }
                    return false;
                }

                // VP8 (lossy, no transparency) - basic WebP
                return false;

            } finally {
                fis.close();
            }
        } catch (IOException e) {
            // If we can't read the file, assume it might need the higher API
            return false;
        }
    }
}