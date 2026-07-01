package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceContext;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NonNull;

import java.nio.charset.StandardCharsets;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {
    public static final Issue ISSUE = Issue.create(
        "WebpUnsupported",
        "WebP format requires API 15+, lossless/transparency requires API 18+",
        "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitBinaryResource(@NonNull BinaryResourceContext context) {
        if (!context.file.getName().endsWith(".webp")) {
            return;
        }

        byte[] bytes = context.getContents();
        if (bytes == null || bytes.length < 16) {
            return;
        }

        if (!matches(bytes, 0, "RIFF") || !matches(bytes, 8, "WEBP")) {
            return;
        }

        String chunk = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        int requiredApi;
        String feature;

        if ("VP8L".equals(chunk)) {
            requiredApi = 18;
            feature = "Lossless WebP";
        } else if ("VP8X".equals(chunk)) {
            requiredApi = 18;
            feature = "Extended WebP (transparency/animation)";
        } else {
            requiredApi = 15;
            feature = "WebP";
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk < requiredApi) {
            String message = String.format("%s requires API level %d (current min is %d)", feature, requiredApi, minSdk);
            context.report(ISSUE, Location.create(context.file), message);
        }
    }

    private boolean matches(byte[] bytes, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}