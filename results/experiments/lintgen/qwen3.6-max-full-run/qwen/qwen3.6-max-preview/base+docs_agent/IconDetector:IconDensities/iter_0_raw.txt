package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\nLow density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
        Category.ICONS, 5, Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private static final List<String> DENSITY_ORDER_LIST = Arrays.asList(
        "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
    private static final Comparator<String> DENSITY_ORDER = Comparator.comparingInt(DENSITY_ORDER_LIST::indexOf);
    private static final Set<String> DENSITIES = new HashSet<>(DENSITY_ORDER_LIST);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "png", "jpg", "jpeg", "gif", "webp", "bmp"));

    private final Map<String, String> iconTypes = new HashMap<>();
    private final Map<String, Map<String, File>> iconDensities = new HashMap<>();

    @Override
    public void checkFolder(Context context, String folderName, File folder) {
        int dash = folderName.indexOf('-');
        String type = dash == -1 ? folderName : folderName.substring(0, dash);
        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        String density = null;
        if (dash != -1) {
            String[] qualifiers = folderName.substring(dash + 1).split("-");
            for (String q : qualifiers) {
                if (DENSITIES.contains(q)) {
                    density = q;
                    break;
                }
            }
        }
        if (density == null) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                if (dot != -1) {
                    String ext = name.substring(dot + 1).toLowerCase(Locale.US);
                    if (IMAGE_EXTENSIONS.contains(ext)) {
                        String baseName = name.substring(0, dot);
                        iconTypes.put(baseName, type);
                        iconDensities.computeIfAbsent(baseName, k -> new HashMap<>()).put(density, f);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Set<String> required = new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"));
        if (includeLdpi) {
            required.add("ldpi");
        }

        for (Map.Entry<String, Map<String, File>> entry : iconDensities.entrySet()) {
            String name = entry.getKey();
            Map<String, File> foundMap = entry.getValue();
            Set<String> found = foundMap.keySet();

            Set<String> missing = new HashSet<>(required);
            missing.removeAll(found);

            if (!missing.isEmpty()) {
                List<String> sortedMissing = new ArrayList<>(missing);
                Collections.sort(sortedMissing, DENSITY_ORDER);
                List<String> sortedFound = new ArrayList<>(found);
                Collections.sort(sortedFound, DENSITY_ORDER);

                String prefix = iconTypes.getOrDefault(name, "drawable");
                String missingStr = sortedMissing.stream()
                    .map(d -> prefix + "-" + d)
                    .collect(Collectors.joining(", "));
                String foundStr = sortedFound.stream()
                    .map(d -> prefix + "-" + d)
                    .collect(Collectors.joining(", "));

                String message = String.format(
                    "Missing the following drawables in %s: %s (found in %s)",
                    name, missingStr, foundStr);

                File locationFile = foundMap.values().iterator().next();
                context.report(ISSUE, Location.create(locationFile), message);
            }
        }

        iconTypes.clear();
        iconDensities.clear();
    }
}