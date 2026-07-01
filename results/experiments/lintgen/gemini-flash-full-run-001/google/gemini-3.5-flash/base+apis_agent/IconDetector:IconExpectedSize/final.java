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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        
        if (!isLauncherIcon(name)) {
            return;
        }

        String folderName = file.getParentFile().getName();
        int expectedSize = getExpectedSize(folderName);
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
                            "Expected size for launcher icon in %s is %dx%d, but was %dx%d",
                            folderName, expectedSize, expectedSize, width, height
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        } catch (IOException e) {
            // Ignore files that cannot be read
        }
    }

    private boolean isLauncherIcon(String name) {
        return name.equals("ic_launcher.png") || 
               name.equals("ic_launcher_round.png") ||
               name.equals("ic_launcher.webp") || 
               name.equals("ic_launcher_round.webp");
    }

    private int getExpectedSize(String folderName) {
        if (folderName.contains("-anydpi")) {
            return -1;
        }
        if (folderName.contains("-xxxhdpi")) {
            return 192;
        } else if (folderName.contains("-xxhdpi")) {
            return 144;
        } else if (folderName.contains("-xhdpi")) {
            return 96;
        } else if (folderName.contains("-hdpi")) {
            return 72;
        } else if (folderName.contains("-mdpi")) {
            return 48;
        } else if (folderName.contains("-ldpi")) {
            return 36;
        }
        
        if (folderName.startsWith("mipmap") || folderName.startsWith("drawable")) {
            return 48;
        }
        return -1;
    }
}