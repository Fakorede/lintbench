package com.android.tools.lint.checks;

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
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NonNull;

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
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_SCOPE
            )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        
        if (!name.endsWith(".png") && !name.endsWith(".webp")) {
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
        int expected = -1;

        if (folderName.contains("-nodpi")) {
            return;
        } else if (folderName.contains("-xxxhdpi")) {
            expected = 192;
        } else if (folderName.contains("-xxhdpi")) {
            expected = 144;
        } else if (folderName.contains("-xhdpi")) {
            expected = 96;
        } else if (folderName.contains("-hdpi")) {
            expected = 72;
        } else if (folderName.contains("-mdpi")) {
            expected = 48;
        } else if (folderName.contains("-ldpi")) {
            expected = 36;
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
                            "Expected size for launcher icon in %s is %dx%d, but was %dx%d",
                            folderName, expected, expected, width, height
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore reading errors
        }
    }
}