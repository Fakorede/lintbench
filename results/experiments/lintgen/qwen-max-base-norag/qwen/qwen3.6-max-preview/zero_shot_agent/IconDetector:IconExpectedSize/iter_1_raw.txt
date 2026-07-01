package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Map<String, Integer> EXPECTED_SIZES = new HashMap<>();
    static {
        EXPECTED_SIZES.put("ldpi", 36);
        EXPECTED_SIZES.put("mdpi", 48);
        EXPECTED_SIZES.put("hdpi", 72);
        EXPECTED_SIZES.put("xhdpi", 96);
        EXPECTED_SIZES.put("xxhdpi", 144);
        EXPECTED_SIZES.put("xxxhdpi", 192);
    }

    @Override
    public void visitBinaryResource(Context context) {
        File file = context.file;
        String name = file.getName();

        if (!isRasterImage(name)) {
            return;
        }

        if (!name.startsWith("ic_launcher")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String folderName = parent.getName();

        if (!folderName.startsWith("drawable") && !folderName.startsWith("mipmap")) {
            return;
        }

        String density = getDensityQualifier(folderName);
        if (density == null) {
            return;
        }

        Integer expected = EXPECTED_SIZES.get(density);
        if (expected == null) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                if (width != expected || height != expected) {
                    String message = String.format(
                            "Expected launcher icon size to be %1$dx%1$d for density %2$s, but was %3$dx%4$d",
                            expected, density, width, height);
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (Exception ignored) {
            // Ignore unreadable or invalid image files
        }
    }

    private boolean isRasterImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") ||
               name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif");
    }

    private String getDensityQualifier(String folderName) {
        int dash = folderName.indexOf('-');
        if (dash == -1) {
            return null;
        }
        String qualifiers = folderName.substring(dash + 1);
        for (String q : qualifiers.split("-")) {
            if (EXPECTED_SIZES.containsKey(q)) {
                return q;
            }
        }
        return null;
    }
}