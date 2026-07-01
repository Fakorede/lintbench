package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();

        if (name.endsWith(".9.png")) {
            return;
        }

        boolean isPng = name.endsWith(".png");
        boolean isJpg = name.endsWith(".jpg") || name.endsWith(".jpeg");

        if (!isPng && !isJpg) {
            return;
        }

        String parentName = file.getParentFile().getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        if (minSdk < 14) {
            return;
        }

        if (isPng && minSdk < 18) {
            try {
                if (hasAlpha(file)) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
        }

        context.report(
                ISSUE,
                Location.create(file),
                "The image format can be converted to WebP for better compression"
        );
    }

    private static boolean hasAlpha(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            byte[] signature = new byte[8];
            if (is.read(signature) != 8) {
                return false;
            }
            if (signature[0] != (byte) 0x89 || signature[1] != (byte) 0x50) {
                return false;
            }

            byte[] ihdr = new byte[18];
            if (is.read(ihdr) != 18) {
                return false;
            }

            int colorType = ihdr[17] & 0xFF;
            return colorType == 4 || colorType == 6;
        }
    }
}