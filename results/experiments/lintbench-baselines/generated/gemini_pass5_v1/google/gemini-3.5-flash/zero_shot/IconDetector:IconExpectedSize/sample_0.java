package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();

        boolean isPng = name.endsWith(".png");
        boolean isWebp = name.endsWith(".webp");
        if (!isPng && !isWebp) {
            return;
        }

        if (!name.startsWith("ic_launcher") || name.contains("foreground") || name.contains("background")) {
            return;
        }

        String folderName = file.getParentFile().getName();
        int expected = -1;
        if (folderName.contains("-mdpi")) {
            expected = 48;
        } else if (folderName.contains("-hdpi")) {
            expected = 72;
        } else if (folderName.contains("-xhdpi")) {
            expected = 96;
        } else if (folderName.contains("-xxhdpi")) {
            expected = 144;
        } else if (folderName.contains("-xxxhdpi")) {
            expected = 192;
        }

        if (expected == -1) {
            return;
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(in);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width != expected || height != expected) {
                            String message = String.format(
                                    "Incorrect icon size for %s: expected %dx%d, but was %dx%d",
                                    folderName, expected, expected, width, height
                            );
                            context.report(ISSUE, Location.create(file), message);
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException e) {
            // Ignore reading errors
        }
    }
}