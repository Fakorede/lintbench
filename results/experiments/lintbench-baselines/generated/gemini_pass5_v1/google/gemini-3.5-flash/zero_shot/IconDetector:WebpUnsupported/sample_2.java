package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.USABILITY,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context) {
        File folder = context.getFolder();
        if (folder == null) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".webp")) {
                checkWebpFile(context, file);
            }
        }
    }

    private void checkWebpFile(@NonNull ResourceContext context, @NonNull File file) {
        int minSdk = 1;
        AndroidVersion minSdkVersion = context.getProject().getMinSdkVersion();
        if (minSdkVersion != null) {
            minSdk = minSdkVersion.getFeatureLevel();
        }

        // If minSdk is already 18 or higher, both 15 and 18 requirements are satisfied.
        if (minSdk >= 18) {
            return;
        }

        byte[] header = new byte[30];
        int bytesRead = 0;
        try (InputStream is = new FileInputStream(file)) {
            while (bytesRead < header.length) {
                int r = is.read(header, bytesRead, header.length - bytesRead);
                if (r == -1) {
                    break;
                }
                bytesRead += r;
            }
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 12) {
            return;
        }

        // Check RIFF and WEBP signature
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
            header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
            return;
        }

        String chunkType = new String(header, 12, 4, StandardCharsets.US_ASCII);
        boolean isLossless = false;
        boolean hasAlpha = false;

        if ("VP8L".equals(chunkType)) {
            isLossless = true;
        } else if ("VP8X".equals(chunkType)) {
            if (bytesRead >= 21) {
                int flags = header[20] & 0xFF;
                hasAlpha = (flags & 0x02) != 0;
            }
        }

        if (minSdk < 15) {
            String message = String.format("WebP format requires Android 4.0 (API 15); current min is %d", minSdk);
            context.report(WEBP_UNSUPPORTED, Location.create(file), message);
        } else if (minSdk < 18 && (isLossless || hasAlpha)) {
            String message = String.format("WebP %s requires Android 4.2.1 (API 18); current min is %d",
                    isLossless ? "lossless encoding" : "transparency", minSdk);
            context.report(WEBP_UNSUPPORTED, Location.create(file), message);
        }
    }
}