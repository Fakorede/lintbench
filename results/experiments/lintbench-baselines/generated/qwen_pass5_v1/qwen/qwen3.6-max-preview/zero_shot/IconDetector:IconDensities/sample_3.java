package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.ResourceFolderInfo;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final boolean INCLUDE_LDPI = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private final Map<String, Set<Density>> iconDensities = new HashMap<>();
    private final Map<String, ResourceFile> iconFiles = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n" +
            "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        iconDensities.clear();
        iconFiles.clear();
    }

    @Override
    public void checkFolder(@NotNull ResourceFolderInfo folder) {
        if (folder.getFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        DensityQualifier densityQualifier = folder.getConfiguration().getDensityQualifier();
        if (densityQualifier == null || densityQualifier.getValue() == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == Density.ANYDPI || density == Density.NODPI) {
            return;
        }
        if (density == Density.LOW && !INCLUDE_LDPI) {
            return;
        }

        for (ResourceFile file : folder.getFiles()) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;

            iconDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
            iconFiles.putIfAbsent(baseName, file);
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        Set<Density> required = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
        if (INCLUDE_LDPI) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, Set<Density>> entry : iconDensities.entrySet()) {
            String name = entry.getKey();
            Set<Density> present = entry.getValue();
            Set<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);

            if (!missing.isEmpty()) {
                ResourceFile file = iconFiles.get(name);
                Location location = context.getLocation(file);
                String missingStr = missing.stream()
                        .map(Density::getResourceValue)
                        .sorted()
                        .collect(Collectors.joining(", "));
                String presentStr = present.stream()
                        .map(Density::getResourceValue)
                        .sorted()
                        .collect(Collectors.joining(", "));

                String message = String.format(
                        "Missing the following drawables in %s: %s (found in %s)",
                        name, missingStr, presentStr);
                context.report(ISSUE, location, message);
            }
        }
    }
}