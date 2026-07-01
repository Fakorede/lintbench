package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). "
                            + "This lint check identifies icons which do not have complete coverage "
                            + "across the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Standard density folders (excluding ldpi by default)
    private static final String[] DENSITY_QUALIFIERS = {
        "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final String[] DENSITY_QUALIFIERS_WITH_LDPI = {
        "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    // Map from icon name to set of densities where it's found
    private final Map<String, Set<String>> mIconsPerDensity = new HashMap<>();
    // Map from density folder to set of icons in that folder
    private final Map<String, Set<String>> mDensityToIcons = new HashMap<>();
    // Track all drawable folders found
    private final Set<String> mDensityFolders = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconsPerDensity.clear();
        mDensityToIcons.clear();
        mDensityFolders.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        String[] densities = includeLdpi ? DENSITY_QUALIFIERS_WITH_LDPI : DENSITY_QUALIFIERS;

        // Find which density folders exist in the project
        Set<String> foundDensities = new HashSet<>();
        for (String density : densities) {
            if (mDensityFolders.contains(density)) {
                foundDensities.add(density);
            }
        }

        if (foundDensities.size() < 2) {
            // Not enough density folders to compare
            return;
        }

        // Collect all icon names
        Set<String> allIcons = new HashSet<>();
        for (Set<String> icons : mDensityToIcons.values()) {
            allIcons.addAll(icons);
        }

        // For each icon, check if it's missing from some densities
        for (String icon : allIcons) {
            Set<String> iconDensities = mIconsPerDensity.get(icon);
            if (iconDensities == null) {
                iconDensities = Collections.emptySet();
            }

            List<String> missingDensities = new ArrayList<>();
            for (String density : densities) {
                if (foundDensities.contains(density) && !iconDensities.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty() && missingDensities.size() < foundDensities.size()) {
                // Icon exists in some densities but not others
                StringBuilder sb = new StringBuilder();
                sb.append("The icon `").append(icon).append("` appears in the following density folders: ");
                List<String> presentDensities = new ArrayList<>(iconDensities);
                Collections.sort(presentDensities);
                sb.append(String.join(", ", presentDensities));
                sb.append(" but not in: ");
                Collections.sort(missingDensities);
                sb.append(String.join(", ", missingDensities));

                context.report(
                        new Incident(
                                ISSUE,
                                context.getProject().getDir(),
                                null,
                                sb.toString()));
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // We handle files directly
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used since getApplicableElements returns null
    }

    /**
     * Called for each resource file in a drawable/mipmap folder.
     * We track which icons appear in which density folders.
     */
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
        String density = getDensityFromFolderName(folderName);
        if (density == null) {
            return;
        }

        mDensityFolders.add(density);

        String iconName = file.getName();
        // Remove extension
        int dot = iconName.lastIndexOf('.');
        if (dot != -1) {
            iconName = iconName.substring(0, dot);
        }

        // Track this icon in the density folder
        Set<String> iconsInDensity = mDensityToIcons.computeIfAbsent(density, k -> new HashSet<>());
        iconsInDensity.add(iconName);

        // Track which densities this icon appears in
        Set<String> densitiesForIcon = mIconsPerDensity.computeIfAbsent(iconName, k -> new HashSet<>());
        densitiesForIcon.add(density);
    }

    /**
     * Extract density qualifier from a drawable/mipmap folder name.
     * E.g., "drawable-hdpi" -> "hdpi", "mipmap-xhdpi" -> "xhdpi"
     */
    private static String getDensityFromFolderName(String folderName) {
        if (folderName == null) {
            return null;
        }

        String[] parts = folderName.split("-");
        if (parts.length < 2) {
            return null;
        }

        // Check if this is a drawable or mipmap folder
        if (!parts[0].equals("drawable") && !parts[0].equals("mipmap")) {
            return null;
        }

        // Look for density qualifier among the parts
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.equals("ldpi") || part.equals("mdpi") || part.equals("hdpi")
                    || part.equals("xhdpi") || part.equals("xxhdpi") || part.equals("xxxhdpi")) {
                return part;
            }
        }

        return null;
    }

    // The following methods are required by the skeleton but not used in this implementation

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }
}