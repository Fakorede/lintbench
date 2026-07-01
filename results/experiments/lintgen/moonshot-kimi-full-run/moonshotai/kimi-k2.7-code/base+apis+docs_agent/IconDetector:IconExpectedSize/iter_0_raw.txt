package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (context.getResourceFolderType() != ResourceFolderType.MIPMAP) {
            return;
        }

        String fileName = context.file.getName();
        String baseName = stripExtension(fileName);
        if (!isLauncherIcon(baseName)) {
            return;
        }

        Density density = Density.MEDIUM;
        FolderConfiguration config = context.getFolderConfiguration();
        if (config != null) {
            DensityQualifier densityQualifier = config.getDensityQualifier();
            if (densityQualifier != null) {
                density = densityQualifier.getValue();
            }
        }

        if (density == Density.ANYDPI || density == Density.NODPI) {
            return;
        }

        int expectedSize = getExpectedLauncherSize(density);
        if (expectedSize <= 0) {
            return;
        }

        int width = context.getImageWidth();
        int height = context.getImageHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "The launcher icon is %dx%d px, but for density `%s` it should be %dx%d px",
                    width, height, density.getResourceValue(), expectedSize, expectedSize);
            context.report(ISSUE, context.getLocation(), message);
        }
    }

    private static boolean isLauncherIcon(String baseName) {
        return "ic_launcher".equals(baseName) || "ic_launcher_round".equals(baseName);
    }

    private static int getExpectedLauncherSize(@NonNull Density density) {
        switch (density) {
            case LOW:
                return 36;
            case MEDIUM:
                return 48;
            case HIGH:
                return 72;
            case XHIGH:
                return 96;
            case XXHIGH:
                return 144;
            case XXXHIGH:
                return 192;
            case TVDPI:
                return 64;
            default:
                return -1;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }
}