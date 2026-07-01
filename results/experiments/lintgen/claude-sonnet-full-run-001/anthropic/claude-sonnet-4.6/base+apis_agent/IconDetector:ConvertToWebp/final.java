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
import java.util.EnumSet;

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
            1,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)
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

        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".jpg")
                && !lowerName.endsWith(".jpeg")) {
            return;
        }

        // Skip .9.png (nine-patch) files — they cannot be converted to WebP
        if (lowerName.endsWith(".9.png")) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        // Only flag drawable and mipmap folders
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        // For PNG files, check if they have transparency (alpha channel).
        // We can still convert transparent PNGs to WebP (lossless with alpha),
        // but we report them regardless since WebP supports it from API 18+.
        // We report all eligible PNG/JPEG files.
        if (lowerName.endsWith(".png")) {
            // Check for animated or special PNGs we should skip
            // Nine-patch already handled above
            reportConvertToWebp(context, file);
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            reportConvertToWebp(context, file);
        }
    }

    private void reportConvertToWebp(@NonNull ResourceContext context, @NonNull File file) {
        String name = file.getName();
        String lowerName = name.toLowerCase();

        String message;
        if (lowerName.endsWith(".png")) {
            // Check if the PNG has an alpha channel
            boolean hasAlpha = hasAlpha(file);
            if (hasAlpha) {
                message = "One or more images in this project can be converted to the WebP format "
                        + "which typically results in smaller file sizes, even for images with "
                        + "transparency/alpha channels. Note that this requires minSdkVersion >= 18.";
            } else {
                message = "One or more images in this project can be converted to the WebP format "
                        + "which typically results in smaller file sizes, even for lossless "
                        + "conversion. Note that this requires minSdkVersion >= 18 for lossless "
                        + "WebP, and minSdkVersion >= 14 for lossy WebP.";
            }
        } else {
            message = "One or more images in this project can be converted to the WebP format "
                    + "which typically results in smaller file sizes. Note that lossy WebP "
                    + "requires minSdkVersion >= 14.";
        }

        Location location = Location.create(file);
        context.report(CONVERT_TO_WEBP, location, message);
    }

    private static boolean hasAlpha(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return image.getColorModel().hasAlpha();
            }
        } catch (IOException ignore) {
            // If we can't read the image, assume no alpha
        }
        return false;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }
}