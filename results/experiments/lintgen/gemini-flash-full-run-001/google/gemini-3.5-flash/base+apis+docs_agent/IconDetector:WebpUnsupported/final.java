package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue UNSUPPORTED = Issue.create(
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
        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        byte[] bytes = new byte[30];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(bytes);
            if (read < 21) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        // Check RIFF and WEBP headers
        if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F' ||
            bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            return;
        }

        if (minSdk < 15) {
            context.report(
                    UNSUPPORTED,
                    Location.create(file),
                    "WebP decoders are not built into Android until Android 4.0 (API 15)"
            );
            return;
        }

        // If minSdk < 18, check for lossless or transparency
        byte c1 = bytes[12];
        byte c2 = bytes[13];
        byte c3 = bytes[14];
        byte c4 = bytes[15];

        if (c1 == 'V' && c2 == 'P' && c3 == '8' && c4 == 'L') {
            context.report(
                    UNSUPPORTED,
                    Location.create(file),
                    "Lossless WebP requires Android 4.2.1 (API 18)"
            );
        } else if (c1 == 'V' && c2 == 'P' && c3 == '8' && c4 == 'X') {
            byte flags = bytes[20];
            boolean hasAlpha = (flags & 0x10) != 0;
            if (hasAlpha) {
                context.report(
                        UNSUPPORTED,
                        Location.create(file),
                        "WebP transparency requires Android 4.2.1 (API 18)"
                );
            }
        }
    }
}