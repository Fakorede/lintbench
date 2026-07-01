package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public EnumSet<ResourceType> getApplicableResourceTypes() {
        return EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP);
    }

    @Override
    public void visitBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        if (file == null || !file.getName().endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        byte[] bytes;
        try {
            bytes = context.readFile();
        } catch (IOException e) {
            return;
        }

        if (bytes.length < 16) {
            return;
        }

        // Verify RIFF header
        if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F') {
            return;
        }
        // Verify WEBP signature
        if (bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            return;
        }

        boolean isLossless = false;
        boolean hasAlpha = false;

        String chunkType = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8L".equals(chunkType)) {
            isLossless = true;
        } else if ("VP8X".equals(chunkType) && bytes.length > 20) {
            int flags = bytes[20] & 0xFF;
            hasAlpha = (flags & 0x10) != 0;
        }

        if (minSdk < 15) {
            context.report(ISSUE, Location.create(file),
                    "WebP requires Android 4.0 (API 15) or higher");
        } else if (minSdk < 18 && (isLossless || hasAlpha)) {
            String feature = isLossless ? "Lossless WebP" : "WebP with transparency";
            context.report(ISSUE, Location.create(file),
                    feature + " requires Android 4.2.1 (API 18) or higher");
        }
    }
}