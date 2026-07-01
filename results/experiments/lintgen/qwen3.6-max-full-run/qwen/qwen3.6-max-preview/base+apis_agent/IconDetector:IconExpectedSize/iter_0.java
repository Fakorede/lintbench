package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceContext;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final Map<String, Integer> EXPECTED_SIZES = new HashMap<>();
    static {
        EXPECTED_SIZES.put("ldpi", 36);
        EXPECTED_SIZES.put("mdpi", 48);
        EXPECTED_SIZES.put("hdpi", 72);
        EXPECTED_SIZES.put("xhdpi", 96);
        EXPECTED_SIZES.put("xxhdpi", 144);
        EXPECTED_SIZES.put("xxxhdpi", 192);
    }

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You " +
            "should follow these conventions to make sure your icons fit in with the " +
            "overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(BinaryResourceContext context) {
        File file = context.getFile();
        String name = file.getName();
        if (!name.endsWith(".png") && !name.endsWith(".webp") && !name.endsWith(".gif")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String folderName = parent.getName();

        if (!folderName.startsWith("mipmap") && !folderName.startsWith("drawable")) {
            return;
        }

        String density = null;
        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (EXPECTED_SIZES.containsKey(part)) {
                density = part;
                break;
            }
        }

        if (density == null) {
            return;
        }

        int expectedSize = EXPECTED_SIZES.get(density);

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Incorrect icon size for %s: expected %dx%d, but was %dx%d",
                        density, expectedSize, expectedSize, width, height);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
            // Skip files that cannot be read as images
        }
    }
}