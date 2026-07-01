package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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
import java.util.EnumSet;

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
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        if (file == null) return;
        String name = file.getName();
        if (!name.endsWith(".png") && !name.endsWith(".webp") && !name.endsWith(".gif") && !name.endsWith(".jpg")) {
            return;
        }

        if (!name.startsWith("ic_launcher")) {
            return;
        }

        String folderName = file.getParentFile().getName();
        Integer expectedSize = null;
        if (folderName.contains("ldpi")) expectedSize = 36;
        else if (folderName.contains("mdpi")) expectedSize = 48;
        else if (folderName.contains("hdpi")) expectedSize = 72;
        else if (folderName.contains("xhdpi")) expectedSize = 96;
        else if (folderName.contains("xxhdpi")) expectedSize = 144;
        else if (folderName.contains("xxxhdpi")) expectedSize = 192;

        if (expectedSize == null) return;

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return;

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Incorrect icon size for %s: expected %dx%d, but was %dx%d",
                        folderName, expectedSize, expectedSize, width, height);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
        }
    }
}