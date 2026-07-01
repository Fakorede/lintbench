package com.android.tools.lint.checks;

import com.android.ide.common.repository.AndroidVersion;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP format requires API 15, lossless/transparency requires API 18",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
                    "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.COMPATIBILITY,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        File file = context.file;
        if (file == null || !file.getName().toLowerCase().endsWith(".webp")) {
            return;
        }

        int requiredApi = getRequiredApiLevel(file);
        if (requiredApi == -1) {
            return;
        }

        AndroidVersion minSdk = context.getMainProject().getMinSdkVersion();
        if (minSdk != null && minSdk.getApiLevel() < requiredApi) {
            String message = String.format(
                    "This WebP image requires API level %d, but the minSdkVersion is %d",
                    requiredApi, minSdk.getApiLevel());
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private int getRequiredApiLevel(@NotNull File file) {
        byte[] header = new byte[21];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(header);
            if (read < 12) {
                return -1;
            }

            // Verify RIFF container
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return -1;
            }
            // Verify WEBP signature
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return -1;
            }

            String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
            if ("VP8L".equals(chunk)) {
                return 18; // Lossless WebP
            } else if ("VP8X".equals(chunk)) {
                if (read >= 21) {
                    byte flags = header[20];
                    if ((flags & 0x10) != 0) { // Alpha/Transparency flag
                        return 18;
                    }
                }
                return 15; // Extended WebP without alpha
            } else if ("VP8 ".equals(chunk)) {
                return 15; // Lossy WebP
            }
        } catch (IOException e) {
            return -1;
        }
        return 15; // Fallback for valid WebP
    }
}