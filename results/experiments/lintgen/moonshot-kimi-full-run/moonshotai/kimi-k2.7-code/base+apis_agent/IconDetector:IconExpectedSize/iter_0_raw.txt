package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.ImageInfo;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Locale;

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
    public void visitBinaryResource(@NonNull ResourceContext context) {
        String folderName = context.getFile().getParentFile().getName();
        if (!folderName.startsWith("mipmap")) {
            return;
        }

        ImageInfo imageInfo = context.getImageInfo();
        if (imageInfo == null) {
            return;
        }

        int expectedSize = getExpectedSize(folderName);
        if (expectedSize <= 0) {
            return;
        }

        int width = imageInfo.width;
        int height = imageInfo.height;
        if (width != expectedSize || height != expectedSize) {
            String message = String.format(Locale.US,
                    "The icon '%1$s' in the %2$s folder has size %3$dx%4$d, but it should be %5$dx%5$d for this density.",
                    context.getFile().getName(), folderName, width, height, expectedSize);
            context.report(ICON_EXPECTED_SIZE, context.getLocation(), message);
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