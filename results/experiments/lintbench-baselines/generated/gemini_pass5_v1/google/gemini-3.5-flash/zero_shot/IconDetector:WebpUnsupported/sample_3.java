package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                    + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.USABILITY,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = 1;
        AndroidVersion minSdkVersion = context.getProject().getMinSdkVersion();
        if (minSdkVersion != null) {
            minSdk = minSdkVersion.getFeatureLevel();
        }

        if (minSdk >= 18) {
            return;
        }

        byte[] header = new byte[30];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(header);
            if (read < 21) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
                header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return;
        }

        if (header[12] == 'V' && header[13] == 'P' && header[14] == '8') {
            byte type = header[15];
            if (type == ' ') {
                if (minSdk < 15) {
                    context.report(ISSUE, context.getLocation(file),
                            "WebP format requires Android 4.0 (API 15); current min SDK is " + minSdk);
                }
            } else if (type == 'L') {
                if (minSdk < 18) {
                    context.report(ISSUE, context.getLocation(file),
                            "WebP lossless format requires Android 4.2.1 (API 18); current min SDK is " + minSdk);
                }
            } else if (type == 'X') {
                int flags = header[20] & 0xFF;
                boolean hasAlpha = (flags & 0x10) != 0;
                boolean hasAnimation = (flags & 0x02) != 0;

                if (hasAlpha || hasAnimation) {
                    if (minSdk < 18) {
                        String feature = hasAlpha ? "transparency" : "animation";
                        context.report(ISSUE, context.getLocation(file),
                                "WebP " + feature + " requires Android 4.2.1 (API 18; API 17 is 4.2.0); current min SDK is " + minSdk);
                    }
                } else {
                    if (minSdk < 15) {
                        context.report(ISSUE, context.getLocation(file),
                                "WebP format requires Android 4.0 (API 15); current min SDK is " + minSdk);
                    }
                }
            }
        }
    }
}