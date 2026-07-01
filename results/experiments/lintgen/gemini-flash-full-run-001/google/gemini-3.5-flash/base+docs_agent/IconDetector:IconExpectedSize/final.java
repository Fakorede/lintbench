package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();

        // Only check launcher icons
        if (!fileName.startsWith("ic_launcher")) {
            return;
        }

        String parentName = file.getParentFile().getName();
        int expectedSize = getExpectedLauncherIconSize(parentName);
        if (expectedSize == -1) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();

                if (width != expectedSize || height != expectedSize) {
                    String message = String.format(
                            "Incorrect icon size for %s: expected %dx%d but was %dx%d",
                            getDensityName(parentName), expectedSize, expectedSize, width, height
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore files that cannot be read as images
        }
    }

    private int getExpectedLauncherIconSize(String folderName) {
        if (folderName.contains("-ldpi")) return 36;
        if (folderName.contains("-mdpi")) return 48;
        if (folderName.contains("-hdpi")) return 72;
        if (folderName.contains("-xhdpi")) return 96;
        if (folderName.contains("-xxhdpi")) return 144;
        if (folderName.contains("-xxxhdpi")) return 192;
        return -1;
    }

    private String getDensityName(String folderName) {
        if (folderName.contains("-ldpi")) return "ldpi";
        if (folderName.contains("-mdpi")) return "mdpi";
        if (folderName.contains("-hdpi")) return "hdpi";
        if (folderName.contains("-xhdpi")) return "xhdpi";
        if (folderName.contains("-xxhdpi")) return "xxhdpi";
        if (folderName.contains("-xxxhdpi")) return "xxxhdpi";
        return "unknown density";
    }
}