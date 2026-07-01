package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE);

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes for launcher icons on each density. You should follow "
                    + "these conventions to make sure your icons fit in with the overall look of "
                    + "the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final Map<Density, Integer> LAUNCHER_ICON_SIZE;
    static {
        Map<Density, Integer> map = new EnumMap<>(Density.class);
        map.put(Density.LOW, 36);
        map.put(Density.MEDIUM, 48);
        map.put(Density.HIGH, 72);
        map.put(Density.XHIGH, 96);
        map.put(Density.XXHIGH, 144);
        map.put(Density.XXXHIGH, 192);
        map.put(Density.TV, 64);
        LAUNCHER_ICON_SIZE = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitBinaryResource(@NonNull ResourceContext context) {
        String fileName = context.getFile().getName();
        String baseName = getBaseName(fileName);
        if (!isLauncherIcon(baseName)) {
            return;
        }

        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null) {
            return;
        }

        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == null
                || density == Density.ANYDPI
                || density == Density.NODPI
                || !LAUNCHER_ICON_SIZE.containsKey(density)) {
            return;
        }

        Integer expected = LAUNCHER_ICON_SIZE.get(density);
        if (expected == null) {
            return;
        }

        BufferedImage image = context.getImage();
        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int expectedSize = expected;

        if (width != expectedSize || height != expectedSize) {
            String message = String.format(
                    "The launcher icon is %1$dx%2$d px, but it should be %3$dx%3$d px for \"%4$s\"",
                    width, height, expectedSize, density.getDisplayValue());
            context.report(ICON_EXPECTED_SIZE, Location.create(context.getFile()), message);
        }
    }

    private static boolean isLauncherIcon(@NonNull String baseName) {
        return "ic_launcher".equals(baseName) || "ic_launcher_round".equals(baseName);
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}