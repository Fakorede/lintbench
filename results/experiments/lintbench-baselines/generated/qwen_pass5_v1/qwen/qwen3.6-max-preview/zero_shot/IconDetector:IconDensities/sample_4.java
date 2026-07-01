package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the " +
        "major screen density classes (low, medium, high, extra high). This lint " +
        "check identifies icons which do not have complete coverage across the densities.\n\n" +
        "Low density is not really used much anymore, so this check ignores the ldpi " +
        "density. To force lint to include it, set the environment variable " +
        "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see " +
        "https://developer.android.com/about/dashboards",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Set<Density> REQUIRED_DENSITIES;
    static {
        Set<Density> set = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
        if (Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            set.add(Density.LOW);
        }
        REQUIRED_DENSITIES = Collections.unmodifiableSet(set);
    }

    private final Map<String, IconData> iconMap = new HashMap<>();

    private static class IconData {
        final String baseName;
        final ResourceFolderType folderType;
        final Set<Density> densities = EnumSet.noneOf(Density.class);
        File representativeFile;
        String extension;
    }

    @Nullable
    @Override
    public List<String> getApplicableFolderNames() {
        return Arrays.asList(
            ResourceFolderType.DRAWABLE.getName(),
            ResourceFolderType.MIPMAP.getName()
        );
    }

    @Override
    public void visitFolder(@NotNull ResourceFolderContext context) {
        Density density = context.getDensity();
        if (density == null || density == Density.NODPI || density == Density.ANYDPI || density == Density.TV) {
            return;
        }

        ResourceFolderType type = context.getFolderType();
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        File folder = context.getFolder();
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() || file.isHidden()) continue;
            String name = file.getName();
            if (name.endsWith(".xml") || name.endsWith(".9.png")) continue;

            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";

            String key = type.getName() + "/" + baseName;
            IconData data = iconMap.get(key);
            if (data == null) {
                data = new IconData();
                data.baseName = baseName;
                data.folderType = type;
                data.representativeFile = file;
                data.extension = ext;
                iconMap.put(key, data);
            }
            data.densities.add(density);
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (IconData data : iconMap.values()) {
            Set<Density> missing = EnumSet.copyOf(REQUIRED_DENSITIES);
            missing.removeAll(data.densities);

            if (!missing.isEmpty()) {
                List<String> missingNames = new ArrayList<>();
                for (Density d : missing) {
                    missingNames.add(d.getResourceValue());
                }
                Collections.sort(missingNames);

                List<String> foundNames = new ArrayList<>();
                for (Density d : data.densities) {
                    foundNames.add(d.getResourceValue());
                }
                Collections.sort(foundNames);

                String message = String.format(
                    "Missing the following drawables in %s: %s%s (found in %s)",
                    String.join(", ", missingNames),
                    data.baseName,
                    data.extension,
                    String.join(", ", foundNames)
                );

                context.report(ISSUE, Location.create(data.representativeFile), message);
            }
        }
        iconMap.clear();
    }
}