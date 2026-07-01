package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE)));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitBinaryResource(@NotNull ResourceContext context) {
        String name = context.file.getName();
        if (!name.toLowerCase().endsWith(".webp")) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(context.file.toPath());
        } catch (IOException e) {
            return;
        }

        if (bytes.length < 30) {
            return;
        }

        // Verify RIFF....WEBP header
        if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F') return;
        if (bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') return;

        boolean needsApi18 = false;
        // Chunk type starts at offset 12
        if (bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8') {
            if (bytes[15] == 'L') {
                // VP8L indicates lossless WebP
                needsApi18 = true;
            } else if (bytes[15] == 'X') {
                // VP8X indicates extended format; check flags byte at offset 20
                // Bit 1 (0x02) represents the Alpha/Transparency flag
                if ((bytes[20] & 0x02) != 0) {
                    needsApi18 = true;
                }
            }
        }

        int requiredApi = needsApi18 ? 18 : 15;
        if (minSdk < requiredApi) {
            String message = needsApi18
                    ? "Lossless or transparent WebP images require API 18 (current min is " + minSdk + ")"
                    : "WebP images require API 15 (current min is " + minSdk + ")";
            context.report(ISSUE, Location.create(context.file), message);
        }
    }
}