package com.android.tools.lint.checks;

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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue WEBP_UNSUPPORTED = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless " +
            "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".webp")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        if (minSdk < 15) {
            context.report(
                    WEBP_UNSUPPORTED,
                    Location.create(file),
                    "WebP requires Android 4.0 (API 15); current min SDK is " + minSdk
            );
            return;
        }

        try {
            WebpHeader header = readWebpHeader(file);
            if (header != null) {
                if (header.lossless) {
                    context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "Lossless WebP requires Android 4.2.1 (API 18); current min SDK is " + minSdk
                    );
                } else if (header.alpha) {
                    context.report(
                            WEBP_UNSUPPORTED,
                            Location.create(file),
                            "WebP with transparency requires Android 4.2.1 (API 18); current min SDK is " + minSdk
                    );
                }
            }
        } catch (IOException e) {
            // Ignore parsing/reading errors
        }
    }

    private static class WebpHeader {
        boolean lossless;
        boolean alpha;
    }

    private static WebpHeader readWebpHeader(File file) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[30];
            int read = bis.read(header);
            if (read < 21) {
                return null;
            }

            // check RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                return null;
            }
            // check WEBP header
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return null;
            }

            WebpHeader webpHeader = new WebpHeader();
            String chunkType = new String(header, 12, 4, "US-ASCII");
            if ("VP8L".equals(chunkType)) {
                webpHeader.lossless = true;
            } else if ("VP8X".equals(chunkType)) {
                int flags = header[20] & 0xFF;
                webpHeader.alpha = (flags & 0x10) != 0;
            }

            return webpHeader;
        }
    }
}