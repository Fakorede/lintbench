package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements ResourceFolderDetector {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int WEBP_LOSSY_MIN_SDK = 14;
    private static final int WEBP_TRANSPARENT_MIN_SDK = 18;

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFile(@NotNull ResourceContext context) {
        File file = context.getFile();
        String name = file.getName();

        if (name.endsWith(".9.png")) {
            return;
        }

        String lower = name.toLowerCase(Locale.US);
        boolean isJpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        boolean isPng = lower.endsWith(".png");

        if (!isJpg && !isPng) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();

        if (minSdk < WEBP_LOSSY_MIN_SDK) {
            return;
        }

        if (minSdk < WEBP_TRANSPARENT_MIN_SDK && isPng && hasAlpha(file)) {
            return;
        }

        Location location = Location.create(file);
        context.report(CONVERT_TO_WEBP, location,
                "This image can be converted to WebP format for better compression");
    }

    private static boolean hasAlpha(@NotNull File file) {
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            return false;
        }

        if (image == null || !image.getColorModel().hasAlpha()) {
            return false;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel & 0xFF000000) != 0xFF000000) {
                    return true;
                }
            }
        }

        return false;
    }
}