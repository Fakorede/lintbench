package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP image format requires Android 4.0 (API 15). WebP images that use "
                    + "lossless encoding or transparency require Android 4.2.1 (API 18).",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.getFile();
        String name = file.getName();
        if (!name.regionMatches(true, name.length() - 5, ".webp", 0, 5)) {
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        int requiredApi = getRequiredWebpApiLevel(data);
        if (requiredApi == -1) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= requiredApi) {
            return;
        }

        String message;
        if (requiredApi == 18) {
            message = String.format(
                    "WebP images with lossless encoding or transparency require Android 4.2.1 "
                            + "(API 18) or later (current minSdk is %d)",
                    minSdk);
        } else {
            message = String.format(
                    "WebP images require Android 4.0 (API 15) or later (current minSdk is %d)",
                    minSdk);
        }

        context.report(WEBP_UNSUPPORTED, Location.create(file), message);
    }

    private static int getRequiredWebpApiLevel(byte[] data) {
        if (data.length < 12
                || !isFourCc(data, 0, "RIFF")
                || !isFourCc(data, 8, "WEBP")) {
            return -1;
        }

        int offset = 12;
        boolean lossless = false;
        boolean alpha = false;
        boolean hasVp8 = false;

        while (offset + 8 <= data.length) {
            String chunkId = new String(data, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = readLittleEndianInt(data, offset + 4);
            int dataStart = offset + 8;

            if (chunkSize < 0 || dataStart > data.length) {
                break;
            }

            if ("VP8X".equals(chunkId) && chunkSize >= 10) {
                int flags = data[dataStart] & 0xFF;
                if ((flags & (1 << 2)) != 0) {
                    alpha = true;
                }
            } else if ("VP8L".equals(chunkId)) {
                lossless = true;
            } else if ("ALPH".equals(chunkId)) {
                alpha = true;
            } else if ("VP8 ".equals(chunkId)) {
                hasVp8 = true;
            }

            int paddedSize = chunkSize + (chunkSize & 1);
            offset = dataStart + paddedSize;

            if (offset < 0 || offset > data.length) {
                break;
            }
        }

        if (lossless || alpha) {
            return 18;
        }
        if (hasVp8) {
            return 15;
        }
        return -1;
    }

    private static boolean isFourCc(byte[] data, int offset, String cc) {
        for (int i = 0; i < 4; i++) {
            if (data[offset + i] != (byte) cc.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}