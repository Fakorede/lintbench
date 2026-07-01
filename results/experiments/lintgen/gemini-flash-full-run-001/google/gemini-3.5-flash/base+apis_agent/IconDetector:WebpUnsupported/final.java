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

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
        "WebpUnsupported",
        "WebP Unsupported",
        "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
        "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
        Category.ICONS,
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
        if (!file.getName().endsWith(".webp")) {
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[30];
            int read = 0;
            while (read < 30) {
                int r = is.read(header, read, 30 - read);
                if (r == -1) {
                    break;
                }
                read += r;
            }

            if (read < 21) {
                return;
            }

            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {

                int minSdk = context.getProject().getMinSdk();

                String chunkType = new String(header, 12, 4, StandardCharsets.US_ASCII);
                if ("VP8L".equals(chunkType)) {
                    if (minSdk < 18) {
                        context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "WebP lossless format requires API 18 (current min is " + minSdk + ")"
                        );
                    }
                } else if ("VP8X".equals(chunkType)) {
                    byte flags = header[20];
                    boolean hasAlpha = (flags & 0x10) != 0;
                    if (hasAlpha && minSdk < 18) {
                        context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "WebP transparency requires API 18 (current min is " + minSdk + ")"
                        );
                    } else if (minSdk < 15) {
                        context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "WebP format requires API 15 (current min is " + minSdk + ")"
                        );
                    }
                } else if ("VP8 ".equals(chunkType)) {
                    if (minSdk < 15) {
                        context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "WebP format requires API 15 (current min is " + minSdk + ")"
                        );
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}