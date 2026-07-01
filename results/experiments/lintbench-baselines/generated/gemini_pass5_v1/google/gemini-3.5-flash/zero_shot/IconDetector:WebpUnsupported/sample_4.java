package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NonNull;

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
                    Scope.BINARY_RESOURCE_FILES_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.getFile();
        if (!file.getName().endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        int folderVersion = -1;
        FolderConfiguration config = context.getFolderConfiguration();
        if (config != null && config.getVersionQualifier() != null) {
            folderVersion = config.getVersionQualifier().getVersion();
        }
        if (folderVersion == -1) {
            String folderName = file.getParentFile().getName();
            for (String segment : folderName.split("-")) {
                if (segment.startsWith("v") && segment.length() > 1) {
                    boolean allDigits = true;
                    for (int i = 1; i < segment.length(); i++) {
                        if (!Character.isDigit(segment.charAt(i))) {
                            allDigits = false;
                            break;
                        }
                    }
                    if (allDigits) {
                        try {
                            folderVersion = Integer.parseInt(segment.substring(1));
                            break;
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
            }
        }

        int effectiveSdk = Math.max(minSdk, folderVersion);
        if (effectiveSdk >= 18) {
            return;
        }

        boolean hasLossless = false;
        boolean hasAlpha = false;
        boolean isWebp = false;

        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12];
            if (is.read(header) == 12) {
                if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                    header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                    isWebp = true;
                }
            }

            if (!isWebp) {
                return;
            }

            while (true) {
                byte[] chunkHeader = new byte[8];
                int read = is.read(chunkHeader);
                if (read < 8) {
                    break;
                }
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = ((chunkHeader[7] & 0xFF) << 24) |
                                ((chunkHeader[6] & 0xFF) << 16) |
                                ((chunkHeader[5] & 0xFF) << 8) |
                                (chunkHeader[4] & 0xFF);

                int paddedSize = (chunkSize + 1) & ~1;

                if ("VP8L".equals(chunkId)) {
                    hasLossless = true;
                } else if ("ALPH".equals(chunkId)) {
                    hasAlpha = true;
                } else if ("VP8X".equals(chunkId)) {
                    if (paddedSize >= 1) {
                        int flags = is.read();
                        if (flags != -1) {
                            if ((flags & 0x10) != 0) {
                                hasAlpha = true;
                            }
                        }
                        long skipped = 0;
                        long toSkip = paddedSize - 1;
                        while (skipped < toSkip) {
                            long s = is.skip(toSkip - skipped);
                            if (s <= 0) break;
                            skipped += s;
                        }
                        continue;
                    }
                }

                long skipped = 0;
                while (skipped < paddedSize) {
                    long s = is.skip(paddedSize - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
            }
        } catch (IOException e) {
            // Ignore descriptor reading issues
        }

        if (!isWebp) {
            return;
        }

        if (effectiveSdk < 15) {
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    "WebP requires Android 4.0 (API 15) or higher (current min SDK is " + effectiveSdk + ")"
            );
        } else if (effectiveSdk < 18 && (hasLossless || hasAlpha)) {
            String feature = hasLossless && hasAlpha ? "lossless encoding and transparency" :
                             hasLossless ? "lossless encoding" : "transparency";
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    "WebP " + feature + " requires Android 4.2.1 (API 18) or higher (current min SDK is " + effectiveSdk + ")"
            );
        }
    }
}