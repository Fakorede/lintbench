package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.USABILITY,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[21];
            int bytesRead = is.read(header);
            if (bytesRead < 21) {
                return;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return;
            }
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return;
            }

            byte c1 = header[12];
            byte c2 = header[13];
            byte c3 = header[14];
            byte c4 = header[15];

            if (c1 == 'V' && c2 == 'P' && c3 == '8' && c4 == ' ') {
                if (minSdk < 15) {
                    context.report(
                            UNSUPPORTED,
                            Location.create(file),
                            "WebP requires Android 4.0 (API 15); current `minSdkVersion` is " + minSdk
                    );
                }
            } else if (c1 == 'V' && c2 == 'P' && c3 == '8' && c4 == 'L') {
                if (minSdk < 18) {
                    context.report(
                            UNSUPPORTED,
                            Location.create(file),
                            "WebP lossless encoding requires Android 4.2.1 (API 18); current `minSdkVersion` is " + minSdk
                    );
                }
            } else if (c1 == 'V' && c2 == 'P' && c3 == '8' && c4 == 'X') {
                byte flags = header[20];
                boolean hasAlpha = (flags & 0x10) != 0;
                if (hasAlpha) {
                    if (minSdk < 18) {
                        context.report(
                                UNSUPPORTED,
                                Location.create(file),
                                "WebP transparency requires Android 4.2.1 (API 18); current `minSdkVersion` is " + minSdk
                        );
                    }
                } else {
                    if (minSdk < 15) {
                        context.report(
                                UNSUPPORTED,
                                Location.create(file),
                                "WebP requires Android 4.0 (API 15); current `minSdkVersion` is " + minSdk
                        );
                    }
                }
            }
        } catch (IOException e) {
            // Ignore format/read errors
        }
    }
}