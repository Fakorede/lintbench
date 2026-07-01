package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
import java.io.IOException;
import java.nio.file.Files;

public class IconDetector extends Detector implements BinaryResourceScanner {

    private static final int WEBP_MIN_API = 15;
    private static final int WEBP_LOSSLESS_TRANSPARENCY_MIN_API = 18;

    private static final int VP8X_FLAG_ALPHA = 0x10;

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                    + "encoding and transparency, require Android 4.2.1 (API 18; API 17 is 4.2.0).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (name.length() < 5 || !name.substring(name.length() - 5).equalsIgnoreCase(".webp")) {
            return;
        }

        Project project = context.getProject();
        int minSdk = project.getMinSdk();
        if (minSdk >= WEBP_LOSSLESS_TRANSPARENCY_MIN_API) {
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }
        if (data == null || data.length < 12) {
            return;
        }

        int requiredApi = getRequiredWebpApi(data);
        if (requiredApi < 0 || minSdk >= requiredApi) {
            return;
        }

        String message;
        if (requiredApi == WEBP_LOSSLESS_TRANSPARENCY_MIN_API) {
            message = String.format(
                    "WebP images with lossless encoding or transparency require Android 4.2.1+ (API %1$d); the current minSdk is %2$d",
                    requiredApi, minSdk);
        } else {
            message = String.format(
                    "WebP images require Android 4.0+ (API %1$d); the current minSdk is %2$d",
                    requiredApi, minSdk);
        }

        Location location = Location.create(file);
        context.report(WEBP_UNSUPPORTED, location, message);
    }

    private static int getRequiredWebpApi(@NonNull byte[] data) {
        if (!isRiffWebP(data)) {
            return -1;
        }

        boolean lossless = false;
        boolean alpha = false;

        int offset = 12;
        while (offset + 8 <= data.length) {
            int chunkSize = readIntLE(data, offset + 4);
            if (chunkSize < 0) {
                break;
            }

            if (isFourCC(data, offset, "VP8 ")) {
                // lossy WebP
            } else if (isFourCC(data, offset, "VP8L")) {
                lossless = true;
            } else if (isFourCC(data, offset, "VP8X")) {
                if (offset + 9 <= data.length) {
                    int flags = data[offset + 8] & 0xFF;
                    if ((flags & VP8X_FLAG_ALPHA) != 0) {
                        alpha = true;
                    }
                }
            }

            int paddedSize = chunkSize + (chunkSize & 1);
            if (offset + 8 + paddedSize < offset || offset + 8 + paddedSize > data.length) {
                break;
            }
            offset += 8 + paddedSize;
        }

        if (lossless || alpha) {
            return WEBP_LOSSLESS_TRANSPARENCY_MIN_API;
        }
        return WEBP_MIN_API;
    }

    private static boolean isRiffWebP(@NonNull byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static boolean isFourCC(@NonNull byte[] data, int offset, @NonNull String fourCC) {
        return data[offset] == fourCC.charAt(0)
                && data[offset + 1] == fourCC.charAt(1)
                && data[offset + 2] == fourCC.charAt(2)
                && data[offset + 3] == fourCC.charAt(3);
    }

    private static int readIntLE(@NonNull byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}