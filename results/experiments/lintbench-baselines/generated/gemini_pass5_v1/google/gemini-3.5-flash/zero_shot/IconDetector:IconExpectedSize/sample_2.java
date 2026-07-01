package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        
        // We only care about bitmap images
        if (!name.endsWith(".png") && !name.endsWith(".webp") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            return;
        }

        // Standard launcher icons are placed in mipmap folders, or named ic_launcher
        boolean isLauncherIcon = context.getFolderType() == ResourceFolderType.MIPMAP || name.startsWith("ic_launcher");
        if (!isLauncherIcon) {
            return;
        }

        String folderName = file.getParentFile().getName();
        int expectedWidth = -1;
        int expectedHeight = -1;

        if (folderName.contains("-ldpi")) {
            expectedWidth = 36;
            expectedHeight = 36;
        } else if (folderName.contains("-mdpi")) {
            expectedWidth = 48;
            expectedHeight = 48;
        } else if (folderName.contains("-hdpi")) {
            expectedWidth = 72;
            expectedHeight = 72;
        } else if (folderName.contains("-xhdpi")) {
            expectedWidth = 96;
            expectedHeight = 96;
        } else if (folderName.contains("-xxhdpi")) {
            expectedWidth = 144;
            expectedHeight = 144;
        } else if (folderName.contains("-xxxhdpi")) {
            expectedWidth = 192;
            expectedHeight = 192;
        }

        if (expectedWidth == -1) {
            return;
        }

        try {
            int[] dimensions = getImageDimensions(file);
            if (dimensions != null) {
                int width = dimensions[0];
                int height = dimensions[1];
                if (width != expectedWidth || height != expectedHeight) {
                    String message = String.format(
                            "Expected launcher icon size for %s is %dx%d, but was %dx%d",
                            folderName, expectedWidth, expectedHeight, width, height
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore reporting errors for unreadable images
        }
    }

    @Nullable
    private static int[] getImageDimensions(@NonNull File file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[] { reader.getWidth(0), reader.getHeight(0) };
                } finally {
                    reader.dispose();
                }
            }
        }
        return null;
    }
}