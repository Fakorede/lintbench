package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;

import java.io.File;
import java.util.*;

public class IconDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). " +
            "This lint check identifies icons which do not have complete coverage " +
            "across the densities.\n\n" +
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
                    Scope.ALL_RESOURCES_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final String[] DENSITY_QUALIFIERS = {
            "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final String[] DENSITY_QUALIFIERS_WITH_LDPI = {
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    // Map from icon name to set of density folders it appears in
    private Map<String, Set<String>> iconMap;
    // Map from density folder to list of icons in that folder
    private Map<String, Set<String>> densityFolderMap;
    // The drawable folders we've seen
    private Set<String> drawableFolders;

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE ||
               folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        iconMap = new HashMap<>();
        densityFolderMap = new HashMap<>();
        drawableFolders = new HashSet<>();
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String folderName = parentFolder.getName();
        String fileName = file.getName();

        // Only care about image files
        if (!isImageFile(fileName)) {
            return;
        }

        String density = getDensityFromFolder(folderName);
        if (density == null) {
            return;
        }

        drawableFolders.add(folderName);

        // Track icon -> densities
        Set<String> densities = iconMap.get(fileName);
        if (densities == null) {
            densities = new HashSet<>();
            iconMap.put(fileName, densities);
        }
        densities.add(density);

        // Track density -> icons
        Set<String> icons = densityFolderMap.get(density);
        if (icons == null) {
            icons = new HashSet<>();
            densityFolderMap.put(density, icons);
        }
        icons.add(fileName);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (iconMap == null || iconMap.isEmpty()) {
            return;
        }

        boolean includeLdpi = isIncludeLdpi();
        String[] densities = includeLdpi ? DENSITY_QUALIFIERS_WITH_LDPI : DENSITY_QUALIFIERS;

        // Find which densities are actually used in this project
        Set<String> usedDensities = new HashSet<>();
        for (String density : densities) {
            if (densityFolderMap.containsKey(density)) {
                usedDensities.add(density);
            }
        }

        // Need at least 2 density folders to do a meaningful comparison
        if (usedDensities.size() < 2) {
            return;
        }

        // For each icon, check if it's missing from any density folder
        // Group missing icons by which densities they're missing from
        Map<String, List<String>> missingByDensity = new TreeMap<>();

        for (Map.Entry<String, Set<String>> entry : iconMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> iconDensities = entry.getValue();

            for (String density : usedDensities) {
                if (!iconDensities.contains(density)) {
                    List<String> missing = missingByDensity.get(density);
                    if (missing == null) {
                        missing = new ArrayList<>();
                        missingByDensity.put(density, missing);
                    }
                    missing.add(iconName);
                }
            }
        }

        if (missingByDensity.isEmpty()) {
            return;
        }

        // Report missing icons
        for (Map.Entry<String, List<String>> entry : missingByDensity.entrySet()) {
            String density = entry.getKey();
            List<String> missingIcons = entry.getValue();
            Collections.sort(missingIcons);

            // Find the drawable folder for this density to report location
            File resDir = context.getProject().getResourceFolders().isEmpty()
                    ? null
                    : context.getProject().getResourceFolders().get(0);

            String folderName = findFolderForDensity(density);
            Location location = Location.create(context.file);

            if (resDir != null && folderName != null) {
                File densityFolder = new File(resDir, folderName);
                if (densityFolder.exists()) {
                    location = Location.create(densityFolder);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Missing the following drawables in ");
            sb.append(folderName != null ? folderName : "drawable-" + density);
            sb.append(": ");
            for (int i = 0; i < missingIcons.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(missingIcons.get(i));
            }
            sb.append(" (found in ");
            // List densities where the icon IS present
            // Actually let's just report the missing ones
            sb.append(getDensitiesWithIcons(missingIcons.get(0)));
            sb.append(")");

            context.report(ICON_DENSITIES, location, sb.toString());
        }
    }

    @Nullable
    private String findFolderForDensity(String density) {
        for (String folder : drawableFolders) {
            String folderDensity = getDensityFromFolder(folder);
            if (density.equals(folderDensity)) {
                return folder;
            }
        }
        return "drawable-" + density;
    }

    private String getDensitiesWithIcons(String iconName) {
        Set<String> densities = iconMap.get(iconName);
        if (densities == null || densities.isEmpty()) {
            return "";
        }
        List<String> sorted = new ArrayList<>(densities);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("drawable-").append(sorted.get(i));
        }
        return sb.toString();
    }

    @Nullable
    private static String getDensityFromFolder(@NonNull String folderName) {
        // Handle both "drawable-hdpi" and "mipmap-hdpi" style folders
        // Also handle compound qualifiers like "drawable-en-hdpi"
        String[] parts = folderName.split("-");
        if (parts.length < 2) {
            return null;
        }

        // Check if the folder is a density-specific drawable/mipmap folder
        String baseName = parts[0];
        if (!baseName.equals("drawable") && !baseName.equals("mipmap")) {
            return null;
        }

        // Look for a density qualifier in the folder name
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.equals("ldpi") || part.equals("mdpi") || part.equals("hdpi") ||
                part.equals("xhdpi") || part.equals("xxhdpi") || part.equals("xxxhdpi")) {
                return part;
            }
        }

        return null;
    }

    private static boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
               lower.endsWith(".webp");
    }

    private static boolean isIncludeLdpi() {
        String env = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        return "true".equals(env);
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }
}