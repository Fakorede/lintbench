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
import java.nio.charset.StandardCharsets;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String EXT_WEBP = "webp";

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP image format requires Android 4.0+",
            "The WebP image format is not supported on Android versions older than 4.0 (API 15). "
                    + "In addition, lossless WebP images and WebP images with transparency "
                    + "require Android 4.2.1+ (API 18).",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context) {
        // Per-file checks are performed in checkFile.
    }

    @Override
    public void checkFile(@NonNull ResourceContext context, @NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String ext = name.substring(dot + 1);
        if (!EXT_WEBP.equalsIgnoreCase(ext)) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk < 15) {
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    "WebP images require Android 4.0 (API 15) or later.");
        } else if (minSdk < 18 && requiresApi18(file)) {
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    "Lossless and transparent WebP images require Android 4.2.1 (API 18) or later.");
        }
    }

    private static boolean requiresApi18(@NonNull File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] riff = new byte[12];
            if (in.read(riff) != 12) {
                return false;
            }
            if (riff[0] != 'R' || riff[1] != 'I' || riff[2] != 'F' || riff[3] != 'F'
                    || riff[8] != 'W' || riff[9] != 'E' || riff[10] != 'B' || riff[11] != 'P') {
                return false;
            }

            byte[] id = new byte[4];
            byte[] sizeBuf = new byte[4];
            if (in.read(id) != 4) {
                return false;
            }
            String chunkId = new String(id, StandardCharsets.US_ASCII);

            if ("VP8L".equals(chunkId)) {
                return true;
            }

            if ("VP8X".equals(chunkId)) {
                if (in.read(sizeBuf) != 4) {
                    return false;
                }
                int chunkSize = (sizeBuf[0] & 0xFF)
                        | ((sizeBuf[1] & 0xFF) << 8)
                        | ((sizeBuf[2] & 0xFF) << 16)
                        | ((sizeBuf[3] & 0xFF) << 24);
                if (chunkSize < 10) {
                    return false;
                }

                byte[] flagsBytes = new byte[4];
                if (in.read(flagsBytes) != 4) {
                    return false;
                }
                int flags = (flagsBytes[0] & 0xFF)
                        | ((flagsBytes[1] & 0xFF) << 8)
                        | ((flagsBytes[2] & 0xFF) << 16)
                        | ((flagsBytes[3] & 0xFF) << 24);
                // Alpha flag in VP8X is 0x00000010.
                if ((flags & 0x10) != 0) {
                    return true;
                }

                int remaining = chunkSize - 4;
                if (remaining > 0) {
                    skip(in, remaining);
                }
                if ((chunkSize & 1) != 0) {
                    skip(in, 1);
                }

                if (in.read(id) != 4) {
                    return false;
                }
                String nextChunk = new String(id, StandardCharsets.US_ASCII);
                return "VP8L".equals(nextChunk);
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static void skip(@NonNull InputStream in, long amount) throws IOException {
        while (amount > 0) {
            long skipped = in.skip(amount);
            if (skipped <= 0) {
                break;
            }
            amount -= skipped;
        }
    }
}