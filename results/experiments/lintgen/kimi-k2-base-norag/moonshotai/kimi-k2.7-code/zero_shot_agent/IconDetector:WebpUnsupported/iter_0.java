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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String WEBP_EXTENSION = ".webp";

    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VP8_ = "VP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VP8L = "VP8L".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VP8X = "VP8X".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ALPH = "ALPH".getBytes(StandardCharsets.US_ASCII);

    private static final int VP8X_ALPHA_FLAG = 0x10;

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        if (!file.getName().endsWith(WEBP_EXTENSION)) {
            return;
        }

        ResourceFolderType folder = ResourceFolderType.getFolderType(file.getParentFile());
        if (folder != ResourceFolderType.DRAWABLE && folder != ResourceFolderType.MIPMAP) {
            return;
        }

        Project project = context.getMainProject();
        int minSdk = project.getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        if (minSdk >= 15) {
            WebpInfo info = parseWebp(file);
            if (info == null || (!info.lossless && !info.alpha)) {
                return;
            }
        }

        String message = minSdk < 15
                ? "WebP is not supported on Android versions older than 4.0 (API 15)"
                : "WebP images with lossless encoding or transparency require Android 4.2.1 (API 18)";

        context.report(WEBP_UNSUPPORTED, Location.create(file), message);
    }

    private static WebpInfo parseWebp(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12];
            if (in.read(header) != 12) {
                return null;
            }
            if (!isSignature(header, 0, RIFF) || !isSignature(header, 8, WEBP)) {
                return null;
            }

            byte[] chunk = new byte[8];
            if (in.read(chunk) != 8) {
                return null;
            }

            String fourcc = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
            int chunkSize = readUInt32(chunk, 4);

            if ("VP8 ".equals(fourcc)) {
                return new WebpInfo(false, false);
            } else if ("VP8L".equals(fourcc)) {
                return new WebpInfo(true, true);
            } else if ("VP8X".equals(fourcc)) {
                if (chunkSize < 1) {
                    return null;
                }
                int flags = in.read();
                if (flags == -1) {
                    return null;
                }
                boolean alpha = (flags & VP8X_ALPHA_FLAG) != 0;

                int vp8xPayload = chunkSize - 1;
                if ((vp8xPayload & 1) != 0) {
                    vp8xPayload++;
                }
                skip(in, vp8xPayload);

                WebpInfo nested = scanChunks(in);
                if (nested == null) {
                    return new WebpInfo(false, alpha);
                }
                return new WebpInfo(nested.lossless, nested.alpha || alpha);
            }
        } catch (IOException e) {
            // Not a valid WebP for our purposes; ignore it.
        }

        return null;
    }

    private static WebpInfo scanChunks(InputStream in) throws IOException {
        boolean alpha = false;
        byte[] chunk = new byte[8];

        while (in.read(chunk) == 8) {
            String fourcc = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
            int chunkSize = readUInt32(chunk, 4);
            int paddedSize = chunkSize + (chunkSize & 1);

            if ("VP8 ".equals(fourcc)) {
                return new WebpInfo(false, alpha);
            } else if ("VP8L".equals(fourcc)) {
                return new WebpInfo(true, true);
            } else if ("ALPH".equals(fourcc)) {
                alpha = true;
                skip(in, paddedSize);
            } else {
                skip(in, paddedSize);
            }
        }

        return new WebpInfo(false, alpha);
    }

    private static int readUInt32(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF)
                | ((buffer[offset + 1] & 0xFF) << 8)
                | ((buffer[offset + 2] & 0xFF) << 16)
                | ((buffer[offset + 3] & 0xFF) << 24);
    }

    private static boolean isSignature(byte[] data, int offset, byte[] signature) {
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static void skip(InputStream in, long amount) throws IOException {
        long remaining = amount;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                break;
            }
            remaining -= skipped;
        }
    }

    private static final class WebpInfo {
        final boolean lossless;
        final boolean alpha;

        WebpInfo(boolean lossless, boolean alpha) {
            this.lossless = lossless;
            this.alpha = alpha;
        }
    }
}