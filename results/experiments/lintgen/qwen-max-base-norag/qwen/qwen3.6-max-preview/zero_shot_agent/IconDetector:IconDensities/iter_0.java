package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.FolderConfiguration;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final boolean INCLUDE_LDPI = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private static final Set<Density> REQUIRED_DENSITIES = EnumSet.of(
            Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH
    );

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n" +
                    "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Set<Density>> iconDensities = new HashMap<>();
    private final Map<String, File> iconFiles = new HashMap<>();
    private final Map<String, String> iconPrefixes = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities.clear();
        iconFiles.clear();
        iconPrefixes.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableFolderNames() {
        return Arrays.asList(SdkConstants.FD_RES_DRAWABLE, SdkConstants.FD_RES_MIPMAP);
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return;

        String extension = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!isImageExtension(extension)) return;

        String baseName = name.substring(0, dot);
        String folderName = file.getParentFile().getName();
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
        if (config == null) return;

        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null || densityQualifier.getValue() == null) return;

        Density density = densityQualifier.getValue();
        if (!REQUIRED_DENSITIES.contains(density) && !(INCLUDE_LDPI && density == Density.LOW)) {
            return;
        }

        iconDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
        iconFiles.putIfAbsent(baseName, file);
        String prefix = folderName.split("-")[0];
        iconPrefixes.putIfAbsent(baseName, prefix);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<Density> required = EnumSet.copyOf(REQUIRED_DENSITIES);
        if (INCLUDE_LDPI) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, Set<Density>> entry : iconDensities.entrySet()) {
            Set<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(entry.getValue());

            if (!missing.isEmpty()) {
                File file = iconFiles.get(entry.getKey());
                String prefix = iconPrefixes.getOrDefault(entry.getKey(), SdkConstants.FD_RES_DRAWABLE);
                Location location = Location.create(file);

                String missingFolders = missing.stream()
                        .map(d -> prefix + "-" + d.getResourceValue())
                        .sorted()
                        .collect(Collectors.joining(", "));

                String foundFolders = entry.getValue().stream()
                        .map(d -> prefix + "-" + d.getResourceValue())
                        .sorted()
                        .collect(Collectors.joining(", "));

                String message = String.format(
                        "Missing the following drawables in `%s`: %s (found in %s)",
                        entry.getKey(),
                        missingFolders,
                        foundFolders
                );
                context.report(ISSUE, location, message);
            }
        }
    }

    private boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("xml") || ext.equals("webp") ||
               ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif");
    }
}