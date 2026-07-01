package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
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
import java.util.EnumSet;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            EnumSet.of(Scope.RESOURCE_FOLDER));

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow " +
            "these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context) {
        File folder = context.getFile();
        Density density = getDensity(folder.getName());
        if (density == null) {
            return;
        }

        int expectedSize = getExpectedLauncherIconSize(density);
        if (expectedSize <= 0) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!isImage(file)) {
                continue;
            }
            if (!isLauncherIcon(file.getName())) {
                continue;
            }

            int[] dimensions = getImageDimensions(file);
            if (dimensions == null) {
                continue;
            }

            int width = dimensions[0];
            int height = dimensions[1];
            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "The launcher icon `%1$s` has dimensions %2$dx%3$d, but for density `%4$s` the expected size is %5$dx%5$d",
                        file.getName(), width, height, density.getResourceValue(), expectedSize);
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }

    @Nullable
    private static Density getDensity(@NonNull String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            Density density = Density.getEnum(part);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    private static int getExpectedLauncherIconSize(@NonNull Density density) {
        if (density == Density.NODPI || density == Density.ANYDPI) {
            return -1;
        }
        return Math.round(48 * density.getDpiValue() / 160f);
    }

    private static boolean isImage(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return (name.endsWith(".png") && !name.endsWith(".9.png"))
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private static boolean isLauncherIcon(@NonNull String fileName) {
        String base = fileName.toLowerCase();
        int dot = base.lastIndexOf('.');
        if (dot != -1) {
            base = base.substring(0, dot);
        }
        return base.equals("ic_launcher") || base.equals("ic_launcher_round") || base.contains("launcher");
    }

    @Nullable
    private static int[] getImageDimensions(@NonNull File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new int[] { image.getWidth(), image.getHeight() };
            }
        } catch (Exception e) {
            // Ignore files that cannot be read as images.
        }
        return null;
    }
}