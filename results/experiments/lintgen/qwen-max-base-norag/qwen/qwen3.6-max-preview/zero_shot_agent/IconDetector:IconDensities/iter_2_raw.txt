package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IconDetector extends ResourceFolderDetector {

    private static final boolean INCLUDE_LDPI = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private static final Set<String> REQUIRED_DENSITIES = new HashSet<>(Arrays.asList(
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    ));

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

    private final Map<String, Set<String>> iconDensities = new HashMap<>();
    private final Map<String, File> iconFiles = new HashMap<>();
    private final Map<String, String> iconFolderTypes = new HashMap<>();

    @Override
    public Collection<String> getApplicableFolderNames() {
        return Arrays.asList("drawable", "mipmap");
    }

    @Override
    public void checkFile(ResourceFile file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return;

        String extension = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!isImageExtension(extension)) return;

        String baseName = name.substring(0, dot);
        String folderName = file.getFolder().getName();

        String density = extractDensity(folderName);
        if (density == null) return;

        Set<String> required = new HashSet<>(REQUIRED_DENSITIES);
        if (INCLUDE_LDPI) required.add("ldpi");

        if (!required.contains(density)) return;

        iconDensities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
        iconFiles.putIfAbsent(baseName, file.getFile());
        String folderType = folderName.split("-")[0];
        iconFolderTypes.putIfAbsent(baseName, folderType);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        Set<String> required = new HashSet<>(REQUIRED_DENSITIES);
        if (INCLUDE_LDPI) required.add("ldpi");

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(entry.getValue());

            if (!missing.isEmpty()) {
                File reportFile = iconFiles.get(entry.getKey());
                String folderType = iconFolderTypes.getOrDefault(entry.getKey(), "drawable");
                Location location = Location.create(reportFile);

                String missingFolders = missing.stream()
                        .map(d -> folderType + "-" + d)
                        .sorted()
                        .collect(Collectors.joining(", "));

                String foundFolders = entry.getValue().stream()
                        .map(d -> folderType + "-" + d)
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

    private String extractDensity(String folderName) {
        String[] parts = folderName.split("-");
        Set<String> allDensities = new HashSet<>(REQUIRED_DENSITIES);
        allDensities.add("ldpi");
        allDensities.add("nodpi");
        allDensities.add("tvdpi");
        allDensities.add("anydpi");

        for (int i = 1; i < parts.length; i++) {
            if (allDensities.contains(parts[i])) {
                return parts[i];
            }
        }
        return null;
    }

    private boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("webp") ||
               ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif");
    }
}