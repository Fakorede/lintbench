package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "WebpUnsupported",
        "WebP Unsupported",
        "The WebP format requires Android 4.0 (API 15). Certain features, " +
        "such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
        Category.USABILITY,
        6,
        Severity.ERROR,
        new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp") && !name.endsWith(".WEBP")) {
            return;
        }

        byte[] header = new byte[30];
        int total = 0;
        try (InputStream is = new FileInputStream(file)) {
            while (total < header.length) {
                int read = is.read(header, total, header.length - total);
                if (read == -1) {
                    break;
                }
                total += read;
            }
        } catch (IOException e) {
            return;
        }

        if (total < 21) {
            return;
        }

        // Check RIFF and WEBP signature
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
            header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        if (minSdk < 15) {
            context.report(
                ISSUE,
                Location.create(file),
                "WebP format requires Android 4.0 (API 15); current min is " + minSdk
            );
            return;
        }

        if (minSdk < 18) {
            String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
            if ("VP8L".equals(chunk)) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "WebP lossless encoding requires Android 4.2.1 (API 18; current min is " + minSdk
                );
            } else if ("VP8X".equals(chunk)) {
                int flags = header[20] & 0xFF;
                boolean alpha = (flags & 0x10) != 0;
                if (alpha) {
                    context.report(
                        ISSUE,
                        Location.create(file),
                        "WebP transparency requires Android 4.2.1 (API 18; current min is " + minSdk
                    );
                }
            }
        }
    }
}