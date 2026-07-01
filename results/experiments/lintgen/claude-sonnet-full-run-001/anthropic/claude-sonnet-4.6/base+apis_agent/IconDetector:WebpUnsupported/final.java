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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as " +
            "lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FOLDER)
            )
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.file;
        if (!folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".webp")) {
                if (minSdk < 15) {
                    String message = "WebP requires Android 4.0 (API 15); current minSdkVersion is " + minSdk;
                    context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            message
                    );
                } else if (minSdk < 18) {
                    if (isLosslessOrTransparentWebp(file)) {
                        String message = "Lossless WebP and WebP with transparency require Android 4.2.1 (API 18); " +
                                "current minSdkVersion is " + minSdk;
                        context.report(
                                WEBP_UNSUPPORTED,
                                Location.create(file),
                                message
                        );
                    }
                }
            }
        }
    }

    private boolean isLosslessOrTransparentWebp(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 16) {
                return false;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return false;
            }

            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return false;
            }

            char c0 = (char) header[12];
            char c1 = (char) header[13];
            char c2 = (char) header[14];
            char c3 = (char) header[15];

            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                return true;
            }

            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                byte[] extHeader = new byte[5];
                int extRead = fis.read(extHeader);
                if (extRead >= 5) {
                    byte flags = extHeader[4];
                    if ((flags & 0x10) != 0) {
                        return true;
                    }
                }
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }
}