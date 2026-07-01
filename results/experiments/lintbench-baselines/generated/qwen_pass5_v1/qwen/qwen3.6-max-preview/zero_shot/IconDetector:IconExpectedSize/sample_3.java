package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
    public void runBinaryResource(@NotNull Context context) throws Exception {
        File file = context.file;
        File parent = file.getParentFile();
        String folderName = parent != null ? parent.getName() : null;
        String fileName = file.getName();

        if (!isLauncherIcon(folderName, fileName)) {
            return;
        }

        String density = getDensity(folderName);
        if (density == null) {
            return;
        }

        Integer expectedSize = EXPECTED_SIZES.get(density);
        if (expectedSize == null) {
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (Exception e) {
            return;
        }

        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "Expected icon size to be %1$dx%1$d but was %2$dx%3$d",
                    expectedSize, width, height);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static boolean isLauncherIcon(String folderName, String fileName) {
        if (folderName != null && folderName.startsWith("mipmap")) {
            return true;
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            return lower.contains("launcher");
        }
        return false;
    }

    private static String getDensity(String folderName) {
        if (folderName == null) {
            return null;
        }
        for (String density : EXPECTED_SIZES.keySet()) {
            if (folderName.contains("-" + density)) {
                return density;
            }
        }
        return null;
    }
}