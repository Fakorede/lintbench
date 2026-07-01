package com.android.tools.lint.checks;

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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private final Map<String, Set<String>> iconDensities = new HashMap<>();
    private final Map<String, File> iconFiles = new HashMap<>();

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NotNull ResourceContext context, @NotNull String folderName) {
        String density = null;
        String[] parts = folderName.split("-");
        for (String part : parts) {
            switch (part) {
                case "ldpi": case "mdpi": case "hdpi": case "xhdpi": case "xxhdpi": case "xxxhdpi":
                case "nodpi": case "anydpi": case "tvdpi":
                    density = part;
                    break;
            }
        }
        if (density == null || "nodpi".equals(density) || "anydpi".equals(density) || "tvdpi".equals(density)) {
            return;
        }

        File folder = context.getFile();
        if (folder == null) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".xml")) continue;
            if (!lower.endsWith(".png") && !lower.endsWith(".webp") && !lower.endsWith(".gif") &&
                !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                continue;
            }

            int dotIndex = name.lastIndexOf('.');
            String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;
            if (baseName.endsWith(".9")) {
                baseName = baseName.substring(0, baseName.length() - 2);
            }

            iconDensities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
            iconFiles.putIfAbsent(baseName, file);
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Set<String> required = new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"));
        if (includeLdpi) {
            required.add("ldpi");
        }

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String baseName = entry.getKey();
            Set<String> found = entry.getValue();
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(found);

            if (!missing.isEmpty()) {
                File file = iconFiles.get(baseName);
                Location location = Location.create(file);
                List<String> sortedMissing = new ArrayList<>(missing);
                Collections.sort(sortedMissing);
                String message = String.format(
                    "Missing density folders for icon `%s`: missing %s",
                    baseName,
                    String.join(", ", sortedMissing)
                );
                context.report(ISSUE, location, message);
            }
        }

        iconDensities.clear();
        iconFiles.clear();
    }
}