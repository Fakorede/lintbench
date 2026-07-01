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
import com.android.tools.lint.detector.api.ResourceFolderScanner;

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
        implements ResourceFolderScanner {

    /** Include ldpi in density checks */
    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    /** The main issue */
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
                    Scope.ALL_RESOURCES_SCOPE));

    private static final String[] DENSITY_QUALIFIERS = {
            "ldpi",
            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi",
            "xxxhdpi"
    };

    private static final String[] REQUIRED_DENSITIES_WITHOUT_LDPI = {
            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi"
    };

    private static final String[] REQUIRED_DENSITIES_WITH_LDPI = {
            "ldpi",
            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi"
    };

    /**
     * Map from icon name to set of densities for which the icon exists.
     * Key: icon file name (e.g. "ic_launcher.png")
     * Value: set of density qualifiers where the icon was found
     */
    private final Map<String, Set<String>> mIconsPerDensity = new HashMap<>();

    /**
     * Map from icon name to a representative file location.
     */
    private final Map<String, File> mIconLocations = new HashMap<>();

    /**
     * Set of density folders found in the project.
     */
    private final Set<String> mDensityFolders = new HashSet<>();

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableFolders() {
        return null; // all folders
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // Determine the density qualifier from the folder name
        String density = getDensityQualifier(folderName);
        if (density != null) {
            mDensityFolders.add(density);
        }

        // List files in this folder
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName();
                if (isIconFile(name)) {
                    // Track this icon
                    Set<String> densities = mIconsPerDensity.get(name);
                    if (densities == null) {
                        densities = new HashSet<>();
                        mIconsPerDensity.put(name, densities);
                    }
                    if (density != null) {
                        densities.add(density);
                    } else {
                        // No density qualifier - treat as "nodpi" or default
                        densities.add("nodpi");
                    }
                    if (!mIconLocations.containsKey(name)) {
                        mIconLocations.put(name, file);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getProject() != context.getMainProject()) {
            return;
        }

        // Determine whether to include ldpi
        boolean includeLdpi = false;
        String env = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        if (env != null && (env.equals("1") || env.equalsIgnoreCase("true"))) {
            includeLdpi = true;
        }

        String[] requiredDensities = includeLdpi
                ? REQUIRED_DENSITIES_WITH_LDPI
                : REQUIRED_DENSITIES_WITHOUT_LDPI;

        // Filter: only check icons that appear in at least one density folder
        // (skip icons that are only in non-density folders like drawable/ or drawable-nodpi/)
        for (Map.Entry<String, Set<String>> entry : mIconsPerDensity.entrySet()) {
            String iconName = entry.getKey();
            Set<String> foundDensities = entry.getValue();

            // Check if icon appears in any density folder at all
            boolean inAnyDensityFolder = false;
            for (String d : DENSITY_QUALIFIERS) {
                if (foundDensities.contains(d)) {
                    inAnyDensityFolder = true;
                    break;
                }
            }

            if (!inAnyDensityFolder) {
                // Icon is only in non-density folders; skip
                continue;
            }

            // Find missing densities
            List<String> missing = new ArrayList<>();
            for (String required : requiredDensities) {
                if (!foundDensities.contains(required)) {
                    missing.add(required);
                }
            }

            if (!missing.isEmpty()) {
                File iconFile = mIconLocations.get(iconName);
                Location location = iconFile != null
                        ? Location.create(iconFile)
                        : Location.create(context.getProject().getDir());

                Collections.sort(missing);
                String message = String.format(
                        "Missing density variation folders in `res`: drawable-%1$s (`%2$s`)",
                        formatList(missing),
                        iconName);

                context.report(ICON_DENSITIES, location, message);
            }
        }
    }

    /**
     * Returns the density qualifier from a folder name, or null if none.
     * For example, "drawable-hdpi" returns "hdpi", "drawable-sw600dp-hdpi" returns "hdpi".
     */
    @Nullable
    private static String getDensityQualifier(@NonNull String folderName) {
        for (String density : DENSITY_QUALIFIERS) {
            // Check for exact match with qualifier
            if (folderName.equals("drawable-" + density)
                    || folderName.equals("mipmap-" + density)
                    || folderName.contains("-" + density + "-")
                    || folderName.endsWith("-" + density)) {
                return density;
            }
        }
        return null;
    }

    /**
     * Returns true if the file name looks like an icon file (png, jpg, gif, webp, xml).
     */
    private static boolean isIconFile(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase();
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")
                || ext.equals("gif") || ext.equals("webp");
        // We intentionally skip .xml files (vector drawables, etc.)
    }

    /**
     * Formats a list of strings as a comma-separated string.
     */
    @NonNull
    private static String formatList(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                if (i == items.size() - 1) {
                    sb.append(", ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}