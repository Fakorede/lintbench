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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "WebpUnsupported",
        "WebP format requires API 15+, and lossless/transparency requires API 18+",
        "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0).",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.toLowerCase(Locale.US).endsWith(".webp")) {
            return;
        }

        byte[] header = new byte[21];
        int read;
        try (InputStream is = new FileInputStream(file)) {
            read = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (read < 16) {
            return;
        }

        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
            header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return;
        }

        String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
        int minApi = 15;
        String feature = null;

        if ("VP8L".equals(chunk)) {
            minApi = 18;
            feature = "Lossless WebP";
        } else if ("VP8X".equals(chunk)) {
            if (read >= 21 && (header[20] & 0x10) != 0) {
                minApi = 18;
                feature = "Transparent WebP";
            }
        } else if (!"VP8 ".equals(chunk)) {
            return;
        }

        Integer minSdkVersion = context.getMainProject().getMinSdkVersion();
        int minSdk = minSdkVersion != null ? minSdkVersion : 1;
        if (minSdk >= minApi) {
            return;
        }

        String message = feature != null
            ? String.format("%s requires API level %d (current min is %d)", feature, minApi, minSdk)
            : String.format("WebP requires API level %d (current min is %d)", minApi, minSdk);

        context.report(ISSUE, Location.create(file), message);
    }
}