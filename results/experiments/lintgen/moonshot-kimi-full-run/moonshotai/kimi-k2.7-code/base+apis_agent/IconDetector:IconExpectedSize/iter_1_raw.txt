package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LocationType;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File resource = context.getResource();
        if (resource == null) {
            return;
        }

        File parent = resource.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        if (!folderName.startsWith("mipmap")) {
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(resource);
        } catch (IOException e) {
            return;
        }

        if (image == null) {
            return;
        }

        int expectedSize = getExpectedSize(folderName);
        if (expectedSize <= 0) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width != expectedSize || height != expectedSize) {
            String message = String.format(Locale.US,
                    "The icon '%1$s' in the %2$s folder has size %3$dx%4$d, but it should be %5$dx%5$d for this density.",
                    resource.getName(), folderName, width, height, expectedSize);
            context.report(ICON_EXPECTED_SIZE,
                    context.getLocation(resource, LocationType.DEFAULT), message);
        }
    }

    private static int getExpectedSize(String folderName) {
        if (folderName.equals("mipmap")) {
            return 48;
        }

        for (String part : folderName.split("-")) {
            if (part.equals("ldpi")) {
                return 36;
            } else if (part.equals("mdpi")) {
                return 48;
            } else if (part.equals("tvdpi")) {
                return 64;
            } else if (part.equals("hdpi")) {
                return 72;
            } else if (part.equals("xhdpi")) {
                return 96;
            } else if (part.equals("xxhdpi")) {
                return 144;
            } else if (part.equals("xxxhdpi")) {
                return 192;
            }
        }

        return -1;
    }
}