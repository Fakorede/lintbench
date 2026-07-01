package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;

import java.io.File;
import java.util.*;

/**
 * Checks for icons that are missing density variants.
 */
public class IconDetector extends Detector implements ResourceFolderScanner {

    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). " +
            "This lint check identifies icons which do not have complete coverage " +
            "across the densities.\n" +
            "\n" +
            "Low density is not really used much anymore, so this check ignores " +
            "the ldpi density. To force lint to include it, set the environment " +
            "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
            "current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    // Density folder prefixes we care about (excluding ldpi by default)
    private static final String[] DENSITY_FOLDERS = {
            "drawable-mdpi",
            "drawable-hdpi",
            "drawable-xhdpi",
            "drawable-xxhdpi",
            "drawable-xxxhdpi"
    };

    private static final String[] DENSITY_FOLDERS_WITH_LDPI = {
            "drawable-ldpi",
            "drawable-mdpi",
            "drawable-hdpi",
            "drawable-xhdpi",
            "drawable-xxhdpi",
            "drawable-xxxhdpi"
    };

    // Map from icon name -> set of density folders that contain it
    private final Map<String, Set<String>> iconToDensities = new HashMap<>();

    // Set of all density folders found in the project
    private final Set<String> foundDensityFolders = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        iconToDensities.clear();
        foundDensityFolders.clear();
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // We only care about drawable folders
        if (!folderName.startsWith("drawable")) {
            return;
        }

        boolean includeLdpi = isIncludeLdpi();
        String[] densityFolders = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        boolean isDensityFolder = false;
        for (String density : densityFolders) {
            if (folderName.equals(density)) {
                isDensityFolder = true;
                break;
            }
        }

        if (!isDensityFolder) {
            return;
        }

        foundDensityFolders.add(folderName);

        // List all image files in this folder
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            if (isImageFile(name)) {
                Set<String> densities = iconToDensities.get(name);
                if (densities == null) {
                    densities = new HashSet<>();
                    iconToDensities.put(name, densities);
                }
                densities.add(folderName);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (foundDensityFolders.isEmpty()) {
            return;
        }

        boolean includeLdpi = isIncludeLdpi();
        String[] densityFolders = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        // Only check against density folders that actually exist in the project
        Set<String> relevantDensities = new HashSet<>();
        for (String density : densityFolders) {
            if (foundDensityFolders.contains(density)) {
                relevantDensities.add(density);
            }
        }

        if (relevantDensities.size() <= 1) {
            // Need at least 2 density folders to complain about missing densities
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();

        for (Map.Entry<String, Set<String>> entry : iconToDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentDensities = entry.getValue();

            Set<String> missingDensities = new HashSet<>(relevantDensities);
            missingDensities.removeAll(presentDensities);

            if (!missingDensities.isEmpty()) {
                // Find the file to report the issue on (use any existing density folder)
                File reportFile = findIconFile(resourceFolders, presentDensities, iconName);

                List<String> sortedMissing = new ArrayList<>(missingDensities);
                Collections.sort(sortedMissing);

                String message = String.format(
                        "The icon `%1$s` is missing from the following density folders: %2$s",
                        iconName,
                        formatList(sortedMissing));

                if (reportFile != null) {
                    Location location = Location.create(reportFile);
                    context.report(ICON_DENSITIES, location, message);
                } else {
                    context.report(ICON_DENSITIES, Location.create(context.getProject().getDir()), message);
                }
            }
        }
    }

    @Nullable
    private File findIconFile(
            @NonNull List<File> resourceFolders,
            @NonNull Set<String> presentDensities,
            @NonNull String iconName) {
        for (File resFolder : resourceFolders) {
            for (String density : presentDensities) {
                File candidate = new File(resFolder, density + File.separator + iconName);
                if (candidate.exists()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isImageFile(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private static boolean isIncludeLdpi() {
        String env = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        return "true".equalsIgnoreCase(env);
    }

    private static String formatList(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                if (i == items.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}