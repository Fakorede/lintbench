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
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP format requires API 15+, lossless/transparency requires API 18+",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.RESOURCE_FILE);
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        if (!file.getName().endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk <= 0) {
            minSdk = 1;
        }
        if (minSdk >= 18) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[21];
            int read = fis.read(header);
            if (read < 16) {
                return;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
                header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return;
            }

            String type = new String(header, 12, 4, StandardCharsets.US_ASCII);
            int requiredApi = 15;
            String feature = "WebP";

            if ("VP8L".equals(type)) {
                requiredApi = 18;
                feature = "Lossless WebP";
            } else if ("VP8X".equals(type)) {
                if (read >= 21 && (header[20] & 0x10) != 0) {
                    requiredApi = 18;
                    feature = "Transparent WebP";
                }
            }

            if (minSdk < requiredApi) {
                String message = String.format(
                        "%s requires API level %d (current minSdk is %d)",
                        feature, requiredApi, minSdk
                );
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
        }
    }
}