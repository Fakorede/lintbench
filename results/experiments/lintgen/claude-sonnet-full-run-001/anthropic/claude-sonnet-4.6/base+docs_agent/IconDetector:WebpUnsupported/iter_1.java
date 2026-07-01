package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.EnumSet;

public class IconDetector extends Detector {

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

    public IconDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        if (minSdk < 15) {
            String message = "WebP requires Android 4.0 (API 15); current minSdkVersion is " + minSdk;
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    message
            );
        } else if (minSdk < 18) {
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

    /**
     * Checks whether a WebP file uses lossless encoding or has transparency (alpha channel).
     */
    private boolean isLosslessOrTransparentWebP(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 16) {
                return false;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            String chunkType = new String(header, 12, 4, "US-ASCII");

            if ("VP8L".equals(chunkType)) {
                return true;
            }

            if ("VP8X".equals(chunkType)) {
                byte[] flags = new byte[4];
                read = fis.read(flags);
                if (read < 4) {
                    return false;
                }
                int flagByte = flags[0] & 0xFF;
                boolean hasAlpha = (flagByte & 0x10) != 0;
                return hasAlpha;
            }

            return false;

        } catch (IOException e) {
            return false;
        }
    }
}