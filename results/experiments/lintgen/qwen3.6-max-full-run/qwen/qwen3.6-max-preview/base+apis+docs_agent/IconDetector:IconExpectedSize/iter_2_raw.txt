package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumSet;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE)));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        ResourceType type = context.getType();
        if (type != ResourceType.MIPMAP && type != ResourceType.DRAWABLE) {
            return;
        }

        String name = context.getName();
        if (name == null || !name.contains("launcher")) {
            return;
        }

        Density density = context.getDensity();
        if (density == null || density == Density.NODPI || density == Density.ANYDPI) {
            return;
        }

        int expectedSize = Math.round(48 * density.getDpiValue() / 160f);
        if (expectedSize <= 0) {
            return;
        }

        File file = context.getFile();
        if (file == null || !file.exists()) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "Expected icon size to be %1$dx%1$d for %2$s density, but was %3$dx%4$d",
                        expectedSize, density.toString(), width, height);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (Exception ignored) {
            // Ignore IO or decoding errors for unsupported formats
        }
    }
}