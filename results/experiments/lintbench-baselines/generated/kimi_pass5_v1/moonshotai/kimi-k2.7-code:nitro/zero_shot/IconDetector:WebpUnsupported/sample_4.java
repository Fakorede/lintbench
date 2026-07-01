package com.android.tools.lint.checks;

import static com.android.SdkConstants.DOT_WEBP;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class IconDetector extends ResourceFolderDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.ALL_RESOURCE_FILES);

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as "
                    + "lossless encoding and transparency, requires Android 4.2.1 (API 18; "
                    + "API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final int MIN_WEBP = 15;
    private static final int MIN_WEBP_LOSSLESS_TRANSPARENCY = 18;

    private static final int FEATURE_LOSSLESS = 1;
    private static final int FEATURE_TRANSPARENCY = 2;

    private int mMinSdk = -1;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mMinSdk = context.getProject().getMinSdk();
    }

    @Override
    public void checkFile(@NonNull ResourceContext context, @NonNull File file) {
        String name = file.getName();
        if (!name.endsWith(DOT_WEBP)) {
            return;
        }

        String message = null;

        if (mMinSdk < MIN_WEBP) {
            message = "WebP requires API level 15 (current minSdk is " + mMinSdk + ")";
        } else if (mMinSdk < MIN_WEBP_LOSSLESS_TRANSPARENCY) {
            int features = getWebpFeatures(file);
            if ((features & FEATURE_LOSSLESS) != 0) {
                message = "Lossless WebP requires API level 18 (current minSdk is " + mMinSdk + ")";
            } else if ((features & FEATURE_TRANSPARENCY) != 0) {
                message = "WebP with transparency requires API level 18 (current minSdk is "
                        + mMinSdk + ")";
            }
        }

        if (message != null) {
            context.report(WEBP_UNSUPPORTED, Location.create(file), message);
        }
    }

    private int getWebpFeatures(@NonNull File file) {
        int features = 0;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");

            byte[] header = new byte[12];
            if (raf.read(header) != 12) {
                return 0;
            }

            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                    || header[8] != 'W' || header[9] != 'E' || header[10] != 'B'
                    || header[11] != 'P') {
                return 0;
            }

            byte[] chunkHeader = new byte[8];
            while (raf.read(chunkHeader) == 8) {
                int chunkSize = (chunkHeader[4] & 0xFF)
                        | ((chunkHeader[5] & 0xFF) << 8)
                        | ((chunkHeader[6] & 0xFF) << 16)
                        | ((chunkHeader[7] & 0xFF) << 24);

                if (chunkHeader[0] == 'V' && chunkHeader[1] == 'P'
                        && chunkHeader[2] == '8' && chunkHeader[3] == 'L') {
                    features |= FEATURE_LOSSLESS | FEATURE_TRANSPARENCY;
                    break;
                } else if (chunkHeader[0] == 'V' && chunkHeader[1] == 'P'
                        && chunkHeader[2] == '8' && chunkHeader[3] == 'X') {
                    byte[] vp8x = new byte[10];
                    if (raf.read(vp8x) == 10) {
                        int flags = vp8x[0];
                        if ((flags & 0x10) != 0) {
                            features |= FEATURE_TRANSPARENCY;
                        }
                    }
                    int remaining = chunkSize - 10;
                    if (remaining > 0) {
                        raf.skipBytes(remaining);
                    }
                    if ((chunkSize & 1) != 0) {
                        raf.skipBytes(1);
                    }
                } else if (chunkHeader[0] == 'V' && chunkHeader[1] == 'P'
                        && chunkHeader[2] == '8' && chunkHeader[3] == ' ') {
                    break;
                } else {
                    int skip = chunkSize + (chunkSize & 1);
                    if (raf.skipBytes(skip) != skip) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            // Ignore malformed files.
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }

        return features;
    }
}