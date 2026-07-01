package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP format requires API level 15, and lossless/transparency requires API 18",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE)
    );

    @Override
    public void visitFile(@NotNull Context context, @NotNull File file) {
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        Project project = context.getMainProject();
        Object minSdkObj = project.getMinSdkVersion();
        int minSdk = minSdkObj != null ? ((com.android.ide.common.api.MinSdkVersion) minSdkObj).getApiLevel() : 1;

        if (minSdk >= 18) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[30];
            int read = fis.read(header);
            if (read < 21) {
                return;
            }

            // Verify RIFF container
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return;
            }
            // Verify WEBP tag
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return;
            }

            boolean isLossless = false;
            boolean hasAlpha = false;

            // Chunk FourCC starts at offset 12
            if (header[12] == 'V' && header[13] == 'P' && header[14] == '8') {
                char subtype = (char) header[15];
                if (subtype == 'L') {
                    isLossless = true;
                } else if (subtype == 'X') {
                    // Extended format: flags byte is at offset 20
                    // Bit 4 (0x10) indicates alpha/transparency
                    hasAlpha = (header[20] & 0x10) != 0;
                }
            }

            if (minSdk < 15) {
                String message = "WebP format requires API level 15 (current min is " + minSdk + ")";
                context.report(ISSUE, Location.create(file), message);
            } else if (minSdk < 18 && (isLossless || hasAlpha)) {
                String feature = isLossless ? "Lossless WebP" : "Transparent WebP";
                String message = feature + " requires API level 18 (current min is " + minSdk + ")";
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
            // Ignore IO errors during lint scan
        }
    }
}