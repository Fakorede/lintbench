package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitBinaryResource(@NotNull ResourceContext context) {
        File file = context.file;
        if (file == null || !file.getName().toLowerCase(Locale.US).endsWith(".webp")) {
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
            Location location = Location.create(file);
            context.report(WEBP_UNSUPPORTED, location, message);
        }
    }

    private static byte[] readFileHeader(File file, int maxBytes) { ... }
    private static boolean isWebP(byte[] data) { ... }
    private static int getRequiredApiLevel(byte[] data) { ... }
}