package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.BinaryResourceScanner;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconExpectedSize",
        "Icon has incorrect size",
        "There are predefined sizes (for each density) for launcher icons. You " +
        "should follow these conventions to make sure your icons fit in with the " +
        "overall look of the platform.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!name.endsWith(".png") && !name.endsWith(".webp")) {
            return;
        }

        if (!name.startsWith("ic_launcher")) {
            return;
        }

        String parentName = file.getParentFile().getName();
        int expectedWidth = -1;
        int expectedHeight = -1;
        String density = "";

        if (parentName.contains("-nodpi")) {
            return;
        } else if (parentName.contains("-ldpi")) {
            expectedWidth = 36;
            expectedHeight = 36;
            density = "ldpi";
        } else if (parentName.contains("-mdpi")) {
            expectedWidth = 48;
            expectedHeight = 48;
            density = "mdpi";
        } else if (parentName.contains("-hdpi")) {
            expectedWidth = 72;
            expectedHeight = 72;
            density = "hdpi";
        } else if (parentName.contains("-xhdpi")) {
            expectedWidth = 96;
            expectedHeight = 96;
            density = "xhdpi";
        } else if (parentName.contains("-xxhdpi")) {
            expectedWidth = 144;
            expectedHeight = 144;
            density = "xxhdpi";
        } else if (parentName.contains("-xxxhdpi")) {
            expectedWidth = 192;
            expectedHeight = 192;
            density = "xxxhdpi";
        }

        if (expectedWidth == -1) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                if (width != expectedWidth || height != expectedHeight) {
                    String message = String.format(
                        "Expected size for %s launcher icon in %s is %dx%d, but was %dx%d",
                        name, density, expectedWidth, expectedHeight, width, height
                    );
                    context.report(ISSUE, context.getLocation(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}