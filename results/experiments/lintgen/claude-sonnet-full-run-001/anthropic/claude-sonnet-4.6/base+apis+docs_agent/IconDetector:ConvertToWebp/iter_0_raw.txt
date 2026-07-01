package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n" +
            "\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        String lowerName = name.toLowerCase();

        // Only check PNG and JPEG files
        boolean isPng = lowerName.endsWith(".png");
        boolean isJpeg = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");

        if (!isPng && !isJpeg) {
            return;
        }

        // Skip .9.png (nine-patch) files - they cannot be converted to WebP
        if (lowerName.endsWith(".9.png")) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        // Only check drawable and mipmap folders
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        // Check minimum SDK version - WebP with transparency/lossless requires API 18 (4.3),
        // but basic WebP support is API 17 (4.2.1). For lossless/alpha it's API 18.
        // We'll flag if minSdk >= 18 for PNG (which may have transparency),
        // and minSdk >= 17 for JPEG (which doesn't have transparency).
        int minSdk = context.getProject().getMinSdk();

        if (isPng) {
            // PNG with transparency requires API 18 for lossless WebP with alpha
            // PNG without transparency can use lossy WebP from API 17
            // We require API 18 to safely convert any PNG
            if (minSdk < 18) {
                return;
            }

            // Check if the PNG has transparency (alpha channel)
            // If it does, we need API 18; if not, API 17 is sufficient
            // Since we already checked minSdk >= 18, we can flag all PNGs
            boolean hasAlpha = hasAlpha(file);
            if (hasAlpha && minSdk < 18) {
                return;
            }
        } else {
            // JPEG - no transparency, basic WebP support from API 17
            if (minSdk < 17) {
                return;
            }
        }

        String message;
        if (isPng) {
            message = String.format(
                    "One or more images in this project can be converted to the WebP format " +
                    "which typically results in smaller file sizes, even for lossless conversion. " +
                    "`%1$s` can be converted.",
                    name);
        } else {
            message = String.format(
                    "One or more images in this project can be converted to the WebP format " +
                    "which typically results in smaller file sizes. " +
                    "`%1$s` can be converted.",
                    name);
        }

        Location location = Location.create(file);
        context.report(CONVERT_TO_WEBP, location, message);
    }

    @Override
    @Nullable
    public Collection<String> getApplicableExtensions() {
        return Collections.unmodifiableList(
                java.util.Arrays.asList("png", "jpg", "jpeg")
        );
    }

    /**
     * Returns true if the given PNG file has an alpha channel (transparency).
     */
    private static boolean hasAlpha(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return image.getColorModel().hasAlpha();
            }
        } catch (IOException ignore) {
            // If we can't read the image, assume it might have alpha to be safe
            return true;
        }
        return false;
    }
}