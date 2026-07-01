package com.android.tools.lint.checks;

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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE);

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP image format requires Android 4.0 (API 15). Lossless and transparent "
                    + "WebP images require Android 4.2.1 (API 18).",
            Category.ICONS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp") && !name.endsWith(".WEBP")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        byte[] data = readFile(file);
        if (data == null || data.length < 12) {
            return;
        }

        if (data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F'
                || data[8] != 'W' || data[9] != 'E' || data[10] != 'B' || data[11] != 'P') {
            return;
        }

        boolean lossless = false;
        boolean alpha = false;

        int offset = 12;
        while (offset + 8 <= data.length) {
            String chunk = new String(data, offset, 4, StandardCharsets.US_ASCII);
            int size = (data[offset + 4] & 0xFF)
                    | ((data[offset + 5] & 0xFF) << 8)
                    | ((data[offset + 6] & 0xFF) << 16)
                    | ((data[offset + 7] & 0xFF) << 24);

            if ("VP8L".equals(chunk)) {
                lossless = true;
            } else if ("VP8X".equals(chunk) && size >= 10 && offset + 8 + 10 <= data.length) {
                int flags = data[offset + 8] & 0xFF;
                alpha = (flags & 0x10) != 0;
            }

            if (size < 0) {
                break;
            }

            if ((size & 1) == 1) {
                size++;
            }
            offset += 8 + size;
        }

        if (minSdk < 15) {
            context.report(WEBP_UNSUPPORTED, Location.create(file),
                    String.format(Locale.US,
                            "WebP images are not supported on this device. They require API 15 "
                                    + "(Android 4.0) or higher (current minSdk is %d).", minSdk));
        } else if (lossless || alpha) {
            String feature;
            if (lossless && alpha) {
                feature = "lossless encoding and transparency";
            } else if (lossless) {
                feature = "lossless encoding";
            } else {
                feature = "transparency";
            }
            context.report(WEBP_UNSUPPORTED, Location.create(file),
                    String.format(Locale.US,
                            "WebP images with %s require API 18 (Android 4.2.1) or higher "
                                    + "(current minSdk is %d).", feature, minSdk));
        }
    }

    private static byte[] readFile(File file) {
        long lengthLong = file.length();
        if (lengthLong <= 0 || lengthLong > Integer.MAX_VALUE - 8) {
            return null;
        }
        int length = (int) lengthLong;
        byte[] bytes = new byte[length];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int remaining = length;
            while (remaining > 0) {
                int read = fis.read(bytes, offset, remaining);
                if (read == -1) {
                    break;
                }
                offset += read;
                remaining -= read;
            }
        } catch (IOException e) {
            return null;
        }
        return bytes;
    }
}