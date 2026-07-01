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
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconExpectedSize",
        "Icon has incorrect size",
        "There are predefined sizes (for each density) for launcher icons. You " +
        "should follow these conventions to make sure your icons fit in with the " +
        "overall look of the platform.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.BINARY_RESOURCE)
    );

    private static final Map<String, Integer> EXPECTED_SIZES = new HashMap<>();
    static {
        EXPECTED_SIZES.put("mdpi", 48);
        EXPECTED_SIZES.put("hdpi", 72);
        EXPECTED_SIZES.put("xhdpi", 96);
        EXPECTED_SIZES.put("xxhdpi", 144);
        EXPECTED_SIZES.put("xxxhdpi", 192);
    }

    @Nullable
    @Override
    public List<String> getApplicableFiles() {
        return Arrays.asList(EXTENSION_PNG, EXTENSION_WEBP, EXTENSION_JPG, EXTENSION_GIF);
    }

    @Override
    public void visitBinaryResource(@NotNull Context context) throws IOException {
        File file = context.getFile();
        if (file == null) {
            return;
        }

        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        boolean isMipmap = folderName.startsWith("mipmap-");
        boolean isDrawable = folderName.startsWith("drawable-");

        if (!isMipmap && !isDrawable) {
            return;
        }

        String fileName = file.getName().toLowerCase(Locale.US);
        if (!fileName.contains("ic_launcher") && !isMipmap) {
            return;
        }

        String density = extractDensity(folderName);
        if (density == null) {
            return;
        }

        Integer expectedSize = EXPECTED_SIZES.get(density);
        if (expectedSize == null) {
            return;
        }

        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                "Incorrect icon size for `%s`: expected %dx%d, but was %dx%d",
                density, expectedSize, expectedSize, width, height
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    @Nullable
    private static String extractDensity(@NotNull String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (EXPECTED_SIZES.containsKey(part)) {
                return part;
            }
        }
        return null;
    }
}