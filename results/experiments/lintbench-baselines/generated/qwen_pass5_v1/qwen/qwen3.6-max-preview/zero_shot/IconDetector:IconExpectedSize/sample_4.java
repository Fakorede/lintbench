package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Image;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector {

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
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.IMAGE_FILE_SCOPE));

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.IMAGE_FILE_SCOPE;
    }

    @Override
    public void visitFile(Context context) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        String[] parts = folderName.split("-");
        if (parts.length < 2) {
            return;
        }

        String type = parts[0];
        if (!"mipmap".equals(type) && !"drawable".equals(type)) {
            return;
        }

        String density = parts[1];
        Integer expectedSize = EXPECTED_SIZES.get(density);
        if (expectedSize == null) {
            return;
        }

        try {
            Image image = context.getImage();
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Incorrect icon size for `%s`: expected %dx%d, but was %dx%d",
                        file.getName(), expectedSize, expectedSize, width, height);
                context.report(ISSUE, context.getLocation(file), message);
            }
        } catch (Exception ignored) {
            // Ignore unreadable or invalid image files
        }
    }
}