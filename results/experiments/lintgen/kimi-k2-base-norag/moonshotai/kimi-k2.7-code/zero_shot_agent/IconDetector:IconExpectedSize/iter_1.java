package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
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
            "There are predefined sizes (for each density) for launcher icons. You "
                    + "should follow these conventions to make sure your icons fit in with the "
                    + "overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE)));

    @Override
    public boolean appliesTo(ResourceType resourceType) {
        return resourceType == ResourceType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        Integer expectedSize = getExpectedSize(parent.getName());
        if (expectedSize == null) {
            return;
        }

        int[] size = readImageSize(file);
        if (size == null) {
            return;
        }

        if (size[0] != expectedSize || size[1] != expectedSize) {
            String message = String.format(
                    "The image %1$s has size %2$dx%3$d but should be %4$dx%4$d",
                    file.getName(), size[0], size[1], expectedSize);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static Integer getExpectedSize(String folderName) {
        switch (folderName) {
            case "mipmap":
            case "mipmap-mdpi":
                return 48;
            case "mipmap-hdpi":
                return 72;
            case "mipmap-xhdpi":
                return 96;
            case "mipmap-xxhdpi":
                return 144;
            case "mipmap-xxxhdpi":
                return 192;
            default:
                return null;
        }
    }

    private static int[] readImageSize(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new int[] { image.getWidth(), image.getHeight() };
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}