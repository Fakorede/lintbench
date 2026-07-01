package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high).  This " +
            "lint check identifies icons which do not have complete coverage across " +
            "the densities.\n" +
            "\n" +
            "Low density is not really used much anymore, so this check ignores " +
            "the ldpi density. To force lint to include it, set the environment " +
            "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
            "current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private static final List<Density> DENSITY_ORDER = Arrays.asList(
            Density.LOW,
            Density.MEDIUM,
            Density.HIGH,
            Density.XHIGH,
            Density.XXHIGH,
            Density.XXXHIGH);

    private Map<String, Multimap<Density, File>> mIcons;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mIcons = Maps.newHashMap();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        boolean includeLdpi = Boolean.parseBoolean(
                System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        Set<Density> expected = new HashSet<Density>(DENSITY_ORDER);
        if (!includeLdpi) {
            expected.remove(Density.LOW);
        }

        for (Map.Entry<String, Multimap<Density, File>> entry : mIcons.entrySet()) {
            Multimap<Density, File> densities = entry.getValue();
            Set<Density> present = densities.keySet();
            if (present.isEmpty()) {
                continue;
            }

            Set<Density> missing = new HashSet<Density>(expected);
            missing.removeAll(present);
            if (missing.isEmpty()) {
                continue;
            }

            File file = densities.values().iterator().next();
            String message = String.format(
                    "Missing the following density variations of this icon: %s",
                    formatDensities(missing));
            context.report(ICON_DENSITIES, Location.create(file), message);
        }

        mIcons = null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context,
            @NonNull String folderName) {
        File folder = context.file;
        ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
        if (config == null) {
            return;
        }
        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }
        Density density = densityQualifier.getValue();
        if (density == Density.NODPI || density == Density.ANYDPI) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            if (!isImageFile(name)) {
                continue;
            }

            String baseName = getBaseName(name);
            String key = type.getName() + "/" + baseName;

            Multimap<Density, File> map = mIcons.get(key);
            if (map == null) {
                map = ArrayListMultimap.create();
                mIcons.put(key, map);
            }
            map.put(density, file);
        }
    }

    private static boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.endsWith(".gif") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static String formatDensities(@NonNull Set<Density> densities) {
        StringBuilder sb = new StringBuilder();
        for (Density density : DENSITY_ORDER) {
            if (!densities.contains(density)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(density.getResourceValue());
        }
        return sb.toString();
    }
}