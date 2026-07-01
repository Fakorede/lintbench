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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
                    "encoding and transparency, require Android 4.2.1 (API 18; API 17 is 4.2.0).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @NotNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.BINARY_RESOURCE_FILE_SCOPE;
    }

    @Override
    public void visitBinaryResource(@NotNull ResourceContext context) {
        File file = context.getFile();
        if (!file.getName().toLowerCase(Locale.US).endsWith(".webp")) {
            return;
        }

        byte[] header = readFileHeader(file, 1024);
        if (header == null || !isWebP(header)) {
            return;
        }

        int requiredApi = getRequiredApiLevel(header);
        int minSdk = context.getMainProject().getMinSdk();
        if (requiredApi > minSdk) {
            String message;
            if (requiredApi == 18) {
                message = "WebP with lossless encoding or transparency requires Android 4.2.1 (API 18)";
            } else {
                message = "WebP requires Android 4.0 (API 15)";
            }
            Location location = context.getLocation();
            context.report(WEBP_UNSUPPORTED, location, message);
        }
    }

    private static byte[] readFileHeader(File file, int maxBytes) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[maxBytes];
            int read = in.read(buffer);
            if (read <= 0) {
                return null;
            }
            return Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isWebP(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static int getRequiredApiLevel(byte[] data) {
        int offset = 12;
        while (offset + 8 <= data.length) {
            String fourcc = new String(data, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = (data[offset + 4] & 0xFF)
                    | ((data[offset + 5] & 0xFF) << 8)
                    | ((data[offset + 6] & 0xFF) << 16)
                    | ((data[offset + 7] & 0xFF) << 24);
            int paddedSize = chunkSize + (chunkSize & 1);

            if ("VP8L".equals(fourcc)) {
                return 18;
            }
            if ("ALPH".equals(fourcc)) {
                return 18;
            }
            if ("VP8X".equals(fourcc) && offset + 9 <= data.length) {
                int flags = data[offset + 8] & 0xFF;
                if ((flags & 0x10) != 0) {
                    return 18;
                }
            }
            if ("VP8 ".equals(fourcc)) {
                return 15;
            }

            offset += 8 + paddedSize;
        }
        return 15;
    }
}