package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP format requires API 15+, lossless/alpha requires API 18+",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE)));

    @Override
    public void visitBinaryResource(Context context) {
        File file = context.file;
        if (file == null || !file.getName().toLowerCase(Locale.US).endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk < 1) {
            minSdk = 1;
        }
        if (minSdk >= 18) {
            return;
        }

        byte[] contents;
        try {
            contents = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        if (contents == null || contents.length < 16) {
            return;
        }

        if (contents[0] != 'R' || contents[1] != 'I' || contents[2] != 'F' || contents[3] != 'F') return;
        if (contents[8] != 'W' || contents[9] != 'E' || contents[10] != 'B' || contents[11] != 'P') return;

        String chunk = new String(contents, 12, 4, StandardCharsets.US_ASCII);
        int requiredApi = 15;

        if ("VP8L".equals(chunk)) {
            requiredApi = 18;
        } else if ("VP8X".equals(chunk) && contents.length >= 21) {
            byte flags = contents[20];
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
    }
}