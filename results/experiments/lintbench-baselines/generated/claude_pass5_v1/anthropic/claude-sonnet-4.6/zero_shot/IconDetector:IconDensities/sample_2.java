package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for icon density coverage issues.
 */
public class IconDetector extends com.android.tools.lint.detector.api.Detector
        implements com.android.tools.lint.detector.api.Detector.ResourceFolderScanner {

    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    /** The main issue discovered by this detector */
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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)));

    private static final List<String> DENSITY_QUALIFIERS = Arrays.asList(
            "ldpi",
            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi",
            "xxxhdpi"
    );

    private static final List<String> REQUIRED_DENSITIES = Arrays.asList(
            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi"
    );

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".xml"
    ));

    /** Map from icon name to set of densities where it is defined */
    private final Map<String, Set<String>> mIconsPerDensity = new HashMap<>();

    /** Map from density folder name to the set of icons it contains */
    private final Map<String, Set<String>> mDensityFolders = new HashMap<>();

    /** The resource directory */
    private File mResDir;

    @Override
    public Collection<String> getApplicableFolderTypes() {
        return Collections.singletonList("drawable");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconsPerDensity.clear();
        mDensityFolders.clear();
        mResDir = null;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // We handle this in afterCheckRootProject by scanning the res directory
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Find the res directory
        File resDir = findResDir(context);
        if (resDir == null || !resDir.isDirectory()) {
            return;
        }

        boolean includeLdpi = false;
        String ldpiEnv = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        if (ldpiEnv != null && (ldpiEnv.equalsIgnoreCase("true") || ldpiEnv.equals("1"))) {
            includeLdpi = true;
        }

        List<String> requiredDensities = new ArrayList<>(REQUIRED_DENSITIES);
        if (includeLdpi) {
            requiredDensities.add(0, "ldpi");
        }

        // Scan the res directory for drawable folders
        File[] folders = resDir.listFiles();
        if (folders == null) {
            return;
        }

        // Collect all icons per density folder
        for (File folder : folders) {
            if (!folder.isDirectory()) {
                continue;
            }
            String folderName = folder.getName();
            if (!folderName.startsWith("drawable")) {
                continue;
            }

            // Determine the density qualifier for this folder
            String density = getDensityQualifier(folderName);
            if (density == null) {
                continue;
            }

            // Only care about density qualifiers we know about
            if (!DENSITY_QUALIFIERS.contains(density) && !density.equals("nodpi") && !density.equals("anydpi")) {
                continue;
            }

            // Skip nodpi and anydpi
            if (density.equals("nodpi") || density.equals("anydpi")) {
                continue;
            }

            Set<String> iconsInFolder = new HashSet<>();
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String name = file.getName();
                        if (isImageFile(name)) {
                            iconsInFolder.add(name);
                        }
                    }
                }
            }

            mDensityFolders.put(density, iconsInFolder);

            // Update the per-icon density map
            for (String icon : iconsInFolder) {
                Set<String> densitiesForIcon = mIconsPerDensity.get(icon);
                if (densitiesForIcon == null) {
                    densitiesForIcon = new HashSet<>();
                    mIconsPerDensity.put(icon, densitiesForIcon);
                }
                densitiesForIcon.add(density);
            }
        }

        // Now check for icons that are missing from some density folders
        // Only check densities that actually have drawable folders
        Set<String> existingDensities = mDensityFolders.keySet();

        // Filter required densities to only those that exist in the project
        List<String> applicableDensities = new ArrayList<>();
        for (String density : requiredDensities) {
            if (existingDensities.contains(density)) {
                applicableDensities.add(density);
            }
        }

        if (applicableDensities.size() < 2) {
            // Not enough density folders to make a meaningful comparison
            return;
        }

        // Find icons that are not present in all applicable density folders
        Map<String, List<String>> missingMap = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : mIconsPerDensity.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densitiesWithIcon = entry.getValue();

            List<String> missingDensities = new ArrayList<>();
            for (String density : applicableDensities) {
                if (!densitiesWithIcon.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty()) {
                missingMap.put(iconName, missingDensities);
            }
        }

        if (missingMap.isEmpty()) {
            return;
        }

        // Report the issues
        // Group by missing densities to reduce noise
        Map<String, List<String>> byMissingDensity = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : missingMap.entrySet()) {
            String iconName = entry.getKey();
            String key = entry.getValue().toString();
            List<String> icons = byMissingDensity.get(key);
            if (icons == null) {
                icons = new ArrayList<>();
                byMissingDensity.put(key, icons);
            }
            icons.add(iconName);
        }

        for (Map.Entry<String, List<String>> entry : byMissingDensity.entrySet()) {
            List<String> icons = entry.getValue();
            Collections.sort(icons);

            // Find a representative icon to attach the location to
            String firstIcon = icons.get(0);
            List<String> missingDensities = missingMap.get(firstIcon);

            // Find the location: use the first density folder that has this icon
            Location location = getLocationForIcon(resDir, firstIcon, applicableDensities,
                    mIconsPerDensity.get(firstIcon));

            StringBuilder sb = new StringBuilder();
            sb.append("The following icons appear in some density folders but not others: ");

            // List the icons
            boolean firstIconEntry = true;
            for (String icon : icons) {
                if (!firstIconEntry) {
                    sb.append(", ");
                }
                firstIconEntry = false;
                // Strip extension for display
                String displayName = icon;
                sb.append(displayName);
            }

            sb.append(" (missing from ").append(formatList(missingDensities)).append(")");

            context.report(ICON_DENSITIES, location, sb.toString());
        }
    }

    @Nullable
    private File findResDir(@NonNull Context context) {
        // Try to find the res directory from the project
        com.android.tools.lint.detector.api.Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders != null && !resourceFolders.isEmpty()) {
            return resourceFolders.get(0);
        }

        // Fallback: try to find from the main project directory
        File dir = project.getDir();
        File resDir = new File(dir, "res");
        if (resDir.isDirectory()) {
            return resDir;
        }

        return null;
    }

    @Nullable
    private String getDensityQualifier(@NonNull String folderName) {
        // drawable alone has no density qualifier
        if (folderName.equals("drawable")) {
            return null;
        }

        // Must start with "drawable-"
        if (!folderName.startsWith("drawable-")) {
            return null;
        }

        String qualifiers = folderName.substring("drawable-".length());

        // Check each part of the qualifier string
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (DENSITY_QUALIFIERS.contains(part) || part.equals("nodpi") || part.equals("anydpi")) {
                return part;
            }
        }

        return null;
    }

    private boolean isImageFile(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = name.substring(dot).toLowerCase();
        return IMAGE_EXTENSIONS.contains(ext);
    }

    @NonNull
    private Location getLocationForIcon(@NonNull File resDir, @NonNull String iconName,
            @NonNull List<String> applicableDensities, @Nullable Set<String> densitiesWithIcon) {
        if (densitiesWithIcon != null) {
            for (String density : applicableDensities) {
                if (densitiesWithIcon.contains(density)) {
                    File folder = findDrawableFolder(resDir, density);
                    if (folder != null) {
                        File iconFile = new File(folder, iconName);
                        if (iconFile.exists()) {
                            return Location.create(iconFile);
                        }
                    }
                }
            }
        }
        return Location.create(resDir);
    }

    @Nullable
    private File findDrawableFolder(@NonNull File resDir, @NonNull String density) {
        File[] folders = resDir.listFiles();
        if (folders == null) {
            return null;
        }
        for (File folder : folders) {
            if (folder.isDirectory() && folder.getName().startsWith("drawable")) {
                String d = getDensityQualifier(folder.getName());
                if (density.equals(d)) {
                    return folder;
                }
            }
        }
        return null;
    }

    @NonNull
    private String formatList(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
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