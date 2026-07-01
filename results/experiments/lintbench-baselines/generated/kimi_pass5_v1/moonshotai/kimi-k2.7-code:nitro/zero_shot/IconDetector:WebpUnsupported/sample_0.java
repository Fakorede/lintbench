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
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP image format requires Android 4.0 (API 15). Lossless and transparent "
                    + "WebP images require Android 4.2.1 (API 18).",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FOLDER_SCOPE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull File folder) {
        Project project = context.getMainProject();
        int minSdk = project.getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".webp")) {
                continue;
            }

            WebpInfo info;
            try {
                info = getWebpInfo(file);
            } catch (IOException e) {
                continue;
            }

            if (info == null) {
                continue;
            }

            if (minSdk < 15) {
                context.report(
                        WEBP_UNSUPPORTED,
                        Location.create(file),
                        "WebP images require API 15 (Android 4.0) or higher."
                );
            } else if (info.lossless || info.alpha) {
                context.report(
                        WEBP_UNSUPPORTED,
                        Location.create(file),
                        "Lossless and transparent WebP images require API 18 "
                                + "(Android 4.2.1) or higher."
                );
            }
        }
    }

    private static WebpInfo getWebpInfo(@NonNull File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            byte[] riff = new byte[12];
            in.readFully(riff);
            if (!isFourCC(riff, 0, "RIFF") || !isFourCC(riff, 8, "WEBP")) {
                return null;
            }

            byte[] fourcc = new byte[4];
            in.readFully(fourcc);

            WebpInfo info = new WebpInfo();

            if (isFourCC(fourcc, 0, "VP8 ")) {
                return info;
            }

            if (isFourCC(fourcc, 0, "VP8L")) {
                info.lossless = true;
                return info;
            }

            if (isFourCC(fourcc, 0, "VP8X")) {
                int chunkSize = readLittleEndianInt(in);
                if (chunkSize < 0) {
                    return null;
                }
                byte[] payload = new byte[chunkSize];
                in.readFully(payload);

                int flags = payload[0] & 0xFF;
                info.alpha = (flags & 0x10) != 0;

                while (true) {
                    try {
                        in.readFully(fourcc);
                    } catch (EOFException e) {
                        break;
                    }

                    int size = readLittleEndianInt(in);
                    if (size < 0) {
                        break;
                    }

                    if (isFourCC(fourcc, 0, "VP8L")) {
                        info.lossless = true;
                        break;
                    }

                    long toSkip = size + (size & 1);
                    while (toSkip > 0) {
                        long skipped = in.skip(toSkip);
                        if (skipped <= 0) {
                            break;
                        }
                        toSkip -= skipped;
                    }
                }

                return info;
            }

            return null;
        } catch (EOFException e) {
            return null;
        }
    }

    private static boolean isFourCC(@NonNull byte[] bytes, int offset, @NonNull String cc) {
        return bytes[offset] == cc.charAt(0)
                && bytes[offset + 1] == cc.charAt(1)
                && bytes[offset + 2] == cc.charAt(2)
                && bytes[offset + 3] == cc.charAt(3);
    }

    private static int readLittleEndianInt(@NonNull DataInputStream in) throws IOException {
        byte[] bytes = new byte[4];
        in.readFully(bytes);
        return (bytes[0] & 0xFF)
                | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 24);
    }

    private static class WebpInfo {
        boolean lossless;
        boolean alpha;
    }
}