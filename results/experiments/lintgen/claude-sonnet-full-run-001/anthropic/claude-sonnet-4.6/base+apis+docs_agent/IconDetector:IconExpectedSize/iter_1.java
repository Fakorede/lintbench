package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
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

    // Expected sizes for launcher icons by density qualifier
    private static final int LDPI_SIZE    = 36;
    private static final int MDPI_SIZE    = 48;
    private static final int HDPI_SIZE    = 72;
    private static final int XHDPI_SIZE   = 96;
    private static final int XXHDPI_SIZE  = 144;
    private static final int XXXHDPI_SIZE = 192;

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();

        // Only check launcher icons (ic_launcher*)
        String baseName = fileName;
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = baseName.substring(0, dotIndex);
        }

        if (!baseName.startsWith("ic_launcher")) {
            return;
        }

        // Determine the density from the folder name
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();
        int expectedSize = getExpectedSize(folderName);
        if (expectedSize <= 0) {
            return;
        }

        // Read the image dimensions
        Dimension size = getImageSize(file);
        if (size == null) {
            return;
        }

        int width = size.width;
        int height = size.height;

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "Launcher icon `%1$s` in `%2$s` should be %3$dx%3$d dp (found %4$dx%5$d dp)",
                    fileName,
                    folderName,
                    expectedSize,
                    width,
                    height
            );
            Location location = Location.create(file);
            context.report(ICON_EXPECTED_SIZE, location, message);
        }
    }

    private static int getExpectedSize(String folderName) {
        if (folderName.contains("-xxxhdpi")) {
            return XXXHDPI_SIZE;
        } else if (folderName.contains("-xxhdpi")) {
            return XXHDPI_SIZE;
        } else if (folderName.contains("-xhdpi")) {
            return XHDPI_SIZE;
        } else if (folderName.contains("-hdpi")) {
            return HDPI_SIZE;
        } else if (folderName.contains("-mdpi")) {
            return MDPI_SIZE;
        } else if (folderName.contains("-ldpi")) {
            return LDPI_SIZE;
        } else if (folderName.equals("drawable") || folderName.equals("mipmap")) {
            // Default density folder — treat as mdpi
            return MDPI_SIZE;
        }
        return -1;
    }

    private static Dimension getImageSize(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            // Ignore; can't read image
        }
        return null;
    }
}