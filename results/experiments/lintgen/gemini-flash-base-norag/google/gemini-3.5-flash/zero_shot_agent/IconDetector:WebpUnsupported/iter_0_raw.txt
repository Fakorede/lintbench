package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_SCOPE)
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        ResourceFolderType folderType = context.getFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp") && !name.endsWith(".WEBP")) {
            return;
        }

        Project project = context.getProject();
        int minSdk = project.getMinSdkVersion().getFeatureLevel();

        WebpInfo info = getWebpInfo(file);
        if (!info.isWebp) {
            return;
        }

        if (minSdk < 15) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    "WebP decoders are not built into Android until API 15 (current min is " + minSdk + ")"
            );
        } else if (minSdk < 18) {
            if (info.isLossless) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "WebP lossless format requires API 18 (current min is " + minSdk + ")"
                );
            } else if (info.hasAlpha) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "WebP transparency requires API 18 (current min is " + minSdk + ")"
                );
            }
        }
    }

    private static WebpInfo getWebpInfo(File file) {
        WebpInfo info = new WebpInfo();
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[30];
            int read = is.read(header);
            if (read < 12) {
                return info;
            }

            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                info.isWebp = true;
            } else {
                return info;
            }

            if (read < 16) {
                return info;
            }

            String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
            if ("VP8L".equals(chunk)) {
                info.isLossless = true;
                info.hasAlpha = true;
            } else if ("VP8X".equals(chunk)) {
                if (read >= 21) {
                    int flags = header[20] & 0xFF;
                    if ((flags & 0x10) != 0) {
                        info.hasAlpha = true;
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return info;
    }

    private static class WebpInfo {
        boolean isWebp = false;
        boolean isLossless = false;
        boolean hasAlpha = false;
    }
}