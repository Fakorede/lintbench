package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            1,
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

        // Skip nine-patch files
        if (lowerName.endsWith(".9.png")) {
            return;
        }

        // Check that we're in a drawable or mipmap folder
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String message = String.format(
                "`%1$s` can be converted to WebP which typically has a smaller file size",
                name);
        context.report(CONVERT_TO_WEBP, Location.create(file), message);
    }

    /**
     * Returns true if the given PNG file has an alpha channel (transparency).
     */
    private static boolean hasTransparency(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return image.getColorModel().hasAlpha();
            }
        } catch (IOException e) {
            // If we can't read the image, assume no transparency
        }
        return false;
    }
}