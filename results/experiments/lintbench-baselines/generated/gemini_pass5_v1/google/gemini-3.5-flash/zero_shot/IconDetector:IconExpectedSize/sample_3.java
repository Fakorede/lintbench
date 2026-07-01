package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.ResourceContext;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        
        if (!(name.startsWith("ic_launcher") && name.endsWith(".png"))) {
            return;
        }

        String parentName = file.getParentFile().getName();
        int expected = -1;
        String density = "";

        if (parentName.contains("-ldpi")) {
            expected = 36;
            density = "ldpi";
        } else if (parentName.contains("-mdpi")) {
            expected = 48;
            density = "mdpi";
        } else if (parentName.contains("-hdpi")) {
            expected = 72;
            density = "hdpi";
        } else if (parentName.contains("-xhdpi")) {
            expected = 96;
            density = "xhdpi";
        } else if (parentName.contains("-xxhdpi")) {
            expected = 144;
            density = "xxhdpi";
        } else if (parentName.contains("-xxxhdpi")) {
            expected = 192;
            density = "xxxhdpi";
        }

        if (expected == -1) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                if (width != expected || height != expected) {
                    String message = String.format(
                            "Launcher icon `%1$s` has size %2$dx%3$d but should be %4$dx%5$d for %6$s",
                            name, width, height, expected, expected, density
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore corrupted or unreadable images
        }
    }
}