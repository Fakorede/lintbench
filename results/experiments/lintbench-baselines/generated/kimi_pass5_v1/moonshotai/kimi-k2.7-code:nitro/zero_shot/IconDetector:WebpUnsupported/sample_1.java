package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final String DOT_WEBP = ".webp";

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP image format is not supported on all versions of Android. "
                    + "WebP requires Android 4.0 (API 15). Lossless and transparent WebP "
                    + "images require Android 4.2.1 (API 18).",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.toLowerCase().endsWith(DOT_WEBP)) {
            return;
        }

        // Only consider drawable / mipmap resources.
        String folder = file.getParentFile().getName();
        if (!folder.startsWith("drawable") && !folder.startsWith("mipmap")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        byte[] header = context.readFile(file);
        if (header == null || header.length < 16) {
            return;
        }

        // Verify RIFF....WEBP signature.
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                || header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return;
        }

        boolean isLossyVp8 = header[12] == 'V' && header[13] == 'P'
                && header[14] == '8' && header[15] == ' ';

        if (isLossyVp8) {
            if (minSdk >= 15) {
                return;
            }
            context.report(WEBP_UNSUPPORTED, Location.create(file),
                    "WebP requires Android 4.0 (API 15)");
        } else {
            // VP8L or VP8X: lossless and/or transparent/animated WebP.
            context.report(WEBP_UNSUPPORTED, Location.create(file),
                    "WebP with lossless encoding or transparency requires Android 4.2.1 (API 18)");
        }
    }
}