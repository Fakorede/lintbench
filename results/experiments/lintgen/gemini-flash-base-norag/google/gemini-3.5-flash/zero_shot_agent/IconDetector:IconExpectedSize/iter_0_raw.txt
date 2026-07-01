package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NonNull;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<ResourceFolderType> applicableFolderTypes() {
        return Arrays.asList(ResourceFolderType.MIPMAP, ResourceFolderType.DRAWABLE);
    }

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context, @NonNull String folderName) {
        File folder = context.getFolder();
        if (folder == null || !folder.exists()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                boolean isLauncher = name.startsWith("ic_launcher");
                boolean isMipmap = folder.getName().startsWith("mipmap");

                if (isLauncher || isMipmap) {
                    checkImageSize(context, file, folder.getName());
                }
            }
        }
    }

    private void checkImageSize(@NonNull ResourceFolderContext context, @NonNull File file, @NonNull String folderName) {
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

        if (expectedWidth > 0) {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width != expectedWidth || height != expectedHeight) {
                        String density = getDensityName(folderName);
                        String message = String.format(
                                "Launcher icon %s has size %dx%d, but should be %dx%d for density %s",
                                file.getName(), width, height, expectedWidth, expectedHeight, density
                        );
                        context.report(ISSUE, Location.create(file), message);
                    }
                }
            } catch (Throwable t) {
                // Ignore read errors to prevent lint from crashing on corrupted images
            }
        }
    }

    @NonNull
    private String getDensityName(@NonNull String folderName) {
        if (folderName.contains("-ldpi")) return "ldpi";
        if (folderName.contains("-mdpi")) return "mdpi";
        if (folderName.contains("-hdpi")) return "hdpi";
        if (folderName.contains("-xhdpi")) return "xhdpi";
        if (folderName.contains("-xxhdpi")) return "xxhdpi";
        if (folderName.contains("-xxxhdpi")) return "xxxhdpi";
        return "unknown";
    }
}