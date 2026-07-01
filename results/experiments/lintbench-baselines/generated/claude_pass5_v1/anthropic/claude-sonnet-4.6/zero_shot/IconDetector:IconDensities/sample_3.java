package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

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
 * Checks for icons that are missing density versions.
 */
public class IconDetector extends ResourceXmlDetector {

    private static final boolean INCLUDE_LDPI;

    static {
        String includeLdpi = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        INCLUDE_LDPI = "true".equals(includeLdpi);
    }

    /** The standard icon density folders */
    private static final List<String> DENSITY_QUALIFIERS;

    static {
        List<String> densities = new ArrayList<>();
        if (INCLUDE_LDPI) {
            densities.add("ldpi");
        }
        densities.add("mdpi");
        densities.add("hdpi");
        densities.add("xhdpi");
        densities.add("xxhdpi");
        densities.add("xxxhdpi");
        DENSITY_QUALIFIERS = Collections.unmodifiableList(densities);
    }

    /** Scope needed to detect this issue */
    private static final EnumSet<Scope> SCOPE = EnumSet.of(Scope.ALL_RESOURCE_FILES);

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
                    SCOPE))
            .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    /**
     * Map from icon name to the set of density folders it appears in.
     * Key: icon file name (e.g. "ic_launcher.png")
     * Value: set of density folder names (e.g. "mdpi", "hdpi", ...)
     */
    private Map<String, Set<String>> mIconsInDensityFolders;

    /**
     * The set of density folders found in the project.
     */
    private Set<String> mDensityFoldersFound;

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconsInDensityFolders = new HashMap<>();
        mDensityFoldersFound = new HashSet<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIconsInDensityFolders == null || mIconsInDensityFolders.isEmpty()) {
            return;
        }

        if (mDensityFoldersFound.size() < 2) {
            // Not enough density folders to complain about
            return;
        }

        // For each icon, check if it appears in all density folders found
        for (Map.Entry<String, Set<String>> entry : mIconsInDensityFolders.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densities = entry.getValue();

            // Find missing densities
            List<String> missingDensities = new ArrayList<>();
            for (String density : mDensityFoldersFound) {
                if (!densities.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty()) {
                Collections.sort(missingDensities);

                // Find a representative file to report the error on
                // Use the first density folder that has this icon
                List<String> presentDensities = new ArrayList<>(densities);
                Collections.sort(presentDensities);

                String firstDensity = presentDensities.get(0);

                // Try to find the actual file to report location on
                File resDir = context.getProject().getResourceFolders().isEmpty()
                        ? null
                        : context.getProject().getResourceFolders().get(0);

                Location location = null;
                if (resDir != null) {
                    // Look for the file in one of the density folders
                    for (String density : presentDensities) {
                        File folder = findDrawableFolder(resDir, density);
                        if (folder != null) {
                            File iconFile = new File(folder, iconName);
                            if (iconFile.exists()) {
                                location = Location.create(iconFile);
                                break;
                            }
                        }
                    }
                }

                if (location == null) {
                    location = Location.create(context.getProject().getDir());
                }

                String message = String.format(
                        "The following image is missing a drawable for the %s density %s: `%s`",
                        missingDensities.size() == 1 ? "density" : "densities",
                        formatDensityList(missingDensities),
                        iconName);

                context.report(ICON_DENSITIES, location, message);
            }
        }
    }

    /**
     * Finds a drawable folder for a given density qualifier in the resource directory.
     */
    @Nullable
    private static File findDrawableFolder(@NonNull File resDir, @NonNull String density) {
        // Check drawable-<density> and mipmap-<density>
        String[] prefixes = {"drawable", "mipmap"};
        for (String prefix : prefixes) {
            File folder = new File(resDir, prefix + "-" + density);
            if (folder.exists() && folder.isDirectory()) {
                return folder;
            }
        }
        return null;
    }

    /**
     * Formats a list of density names for display.
     */
    private static String formatDensityList(@NonNull List<String> densities) {
        if (densities.isEmpty()) {
            return "";
        }
        if (densities.size() == 1) {
            return densities.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < densities.size(); i++) {
            if (i > 0) {
                if (i == densities.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(densities.get(i));
        }
        return sb.toString();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();
        String density = getDensityFromFolder(folderName);

        if (density != null) {
            // This is a density-specific drawable/mipmap folder
            mDensityFoldersFound.add(density);

            // Record that this icon exists in this density folder
            String iconName = file.getName();
            if (isIconFile(iconName)) {
                Set<String> densitiesForIcon = mIconsInDensityFolders.get(iconName);
                if (densitiesForIcon == null) {
                    densitiesForIcon = new HashSet<>();
                    mIconsInDensityFolders.put(iconName, densitiesForIcon);
                }
                densitiesForIcon.add(density);
            }
        }
    }

    /**
     * Returns the density qualifier from a folder name, or null if not a density folder.
     */
    @Nullable
    private static String getDensityFromFolder(@NonNull String folderName) {
        // Folder name format: drawable-<qualifier>[-<qualifier>...] or mipmap-<qualifier>...
        boolean isDrawable = folderName.startsWith("drawable");
        boolean isMipmap = folderName.startsWith("mipmap");

        if (!isDrawable && !isMipmap) {
            return null;
        }

        // Extract qualifiers
        String qualifiers;
        if (isDrawable) {
            if (folderName.equals("drawable")) {
                return null; // default drawable folder, no density
            }
            if (!folderName.startsWith("drawable-")) {
                return null;
            }
            qualifiers = folderName.substring("drawable-".length());
        } else {
            if (folderName.equals("mipmap")) {
                return null;
            }
            if (!folderName.startsWith("mipmap-")) {
                return null;
            }
            qualifiers = folderName.substring("mipmap-".length());
        }

        // Split qualifiers and look for density
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (isDensityQualifier(part)) {
                return part;
            }
        }

        return null;
    }

    /**
     * Returns true if the given string is a known density qualifier.
     */
    private static boolean isDensityQualifier(@NonNull String qualifier) {
        switch (qualifier) {
            case "ldpi":
            case "mdpi":
            case "hdpi":
            case "xhdpi":
            case "xxhdpi":
            case "xxxhdpi":
            case "tvdpi":
            case "nodpi":
            case "anydpi":
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if the given file name is an icon file (image file).
     */
    private static boolean isIconFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    // We override getApplicableElements to return null since we use beforeCheckFile
    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }
}