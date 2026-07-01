package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.ApiVersionInfo;
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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP format requires API 15+, lossless/alpha requires API 18+",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.COMPATIBILITY,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE));

    @Override
    public void visitFile(Context context, File file) {
        if (!file.getName().toLowerCase(Locale.US).endsWith(".webp")) {
            return;
        }

        ApiVersionInfo minSdkInfo = context.getMainProject().getMinSdkVersion();
        int minSdk = minSdkInfo != null ? minSdkInfo.getApiLevel() : 1;
        if (minSdk >= 18) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[21];
            int read = fis.read(header);
            if (read < 16) {
                return;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') return;
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') return;

            String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
            int requiredApi = 15;

            if ("VP8L".equals(chunk)) {
                requiredApi = 18;
            } else if ("VP8X".equals(chunk) && read >= 21) {
                byte flags = header[20];
                if ((flags & 0x10) != 0) {
                    requiredApi = 18;
                }
            }

            if (minSdk < requiredApi) {
                String feature = requiredApi == 18 ? "with lossless encoding or transparency" : "format";
                String message = String.format("WebP %s requires API level %d (current min is %d)",
                        feature, requiredApi, minSdk);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
        }
    }
}