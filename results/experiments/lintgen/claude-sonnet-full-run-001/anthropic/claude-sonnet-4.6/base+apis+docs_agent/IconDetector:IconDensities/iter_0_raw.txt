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
            "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final String[] DENSITY_FOLDERS_WITH_LDPI = {
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    // Map from icon name -> set of density folders that contain it
    // We accumulate this across all resource folders in a project
    private Map<String, Set<String>> mIconsPerDensity;

    // All density folders found in the project
    private Set<String> mDensityFolders;

    // Location map: icon name -> a representative location (from any folder)
    private Map<String, Location> mIconLocations;

    public IconDetector() {
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mIconsPerDensity = new HashMap<>();
        mDensityFolders = new HashSet<>();
        mIconLocations = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mIconsPerDensity == null || mDensityFolders == null) {
            return;
        }

        // Determine which densities to check
        boolean includeLdpi = isLdpiIncluded();
        String[] expectedDensities = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        // Only consider density folders that actually exist in the project
        Set<String> relevantDensities = new HashSet<>();
        for (String density : expectedDensities) {
            if (mDensityFolders.contains(density)) {
                relevantDensities.add(density);
            }
        }

        if (relevantDensities.size() < 2) {
            // Not enough density folders to make a meaningful comparison
            return;
        }

        // Check each icon
        for (Map.Entry<String, Set<String>> entry : mIconsPerDensity.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densitiesWithIcon = entry.getValue();

            // Find which relevant densities are missing this icon
            List<String> missing = new ArrayList<>();
            for (String density : expectedDensities) {
                if (relevantDensities.contains(density) && !densitiesWithIcon.contains(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                // Build message
                Collections.sort(missing);
                StringBuilder sb = new StringBuilder();
                sb.append("The following image is missing a drawable for the following densities: ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missing.get(i));
                }
                sb.append(": ").append(iconName);

                Location location = mIconLocations.get(iconName);
                if (location == null) {
                    context.report(ICON_DENSITIES, Location.create(context.getProject().getDir()),
                            sb.toString());
                } else {
                    context.report(ICON_DENSITIES, location, sb.toString());
                }
            }
        }

        mIconsPerDensity = null;
        mDensityFolders = null;
        mIconLocations = null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // Determine if this folder is a density-specific folder
        String density = getDensityFromFolderName(folderName);
        if (density == null) {
            return;
        }

        mDensityFolders.add(density);

        // List all drawable files in this folder
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName();
                if (isImageFile(fileName)) {
                    // Record that this icon exists in this density
                    Set<String> densities = mIconsPerDensity.get(fileName);
                    if (densities == null) {
                        densities = new HashSet<>();
                        mIconsPerDensity.put(fileName, densities);
                    }
                    densities.add(density);

                    // Store a location for this icon if we don't have one yet
                    if (!mIconLocations.containsKey(fileName)) {
                        mIconLocations.put(fileName, Location.create(file));
                    }
                }
            }
        }
    }

    @Nullable
    private static String getDensityFromFolderName(@NonNull String folderName) {
        // Folder names are like "drawable-mdpi", "drawable-hdpi", "mipmap-xhdpi", etc.
        // We need to extract the density qualifier.
        // Split on '-' and look for known density qualifiers
        String[] parts = folderName.split("-");
        for (String part : parts) {
            switch (part) {
                case "ldpi":
                case "mdpi":
                case "hdpi":
                case "xhdpi":
                case "xxhdpi":
                case "xxxhdpi":
                    return part;
            }
        }
        return null;
    }

    private static boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".xml"); // vector drawables etc. can also be density-specific
    }

    private static boolean isLdpiIncluded() {
        String value = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        return "true".equalsIgnoreCase(value);
    }
}