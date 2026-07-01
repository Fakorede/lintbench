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
                            + "across the densities.\n"
                            + "\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Standard density buckets we check (excluding ldpi by default)
    private static final String DENSITY_MDPI = "mdpi";
    private static final String DENSITY_HDPI = "hdpi";
    private static final String DENSITY_XHDPI = "xhdpi";
    private static final String DENSITY_XXHDPI = "xxhdpi";
    private static final String DENSITY_LDPI = "ldpi";

    // Map from icon name to the set of densities it appears in
    private final Map<String, Set<String>> iconDensityMap = new HashMap<>();

    // Track which density folders we've seen
    private final Set<String> densityFolders = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensityMap.clear();
        densityFolders.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        boolean includeLdpi = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        List<String> requiredDensities = new ArrayList<>();
        requiredDensities.add(DENSITY_MDPI);
        requiredDensities.add(DENSITY_HDPI);
        requiredDensities.add(DENSITY_XHDPI);
        requiredDensities.add(DENSITY_XXHDPI);
        if (includeLdpi) {
            requiredDensities.add(DENSITY_LDPI);
        }

        // Only check densities that are actually present in the project
        List<String> presentDensities = new ArrayList<>();
        for (String density : requiredDensities) {
            if (densityFolders.contains(density)) {
                presentDensities.add(density);
            }
        }

        if (presentDensities.size() < 2) {
            // Not enough density folders to make a meaningful comparison
            return;
        }

        for (Map.Entry<String, Set<String>> entry : iconDensityMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> foundDensities = entry.getValue();

            List<String> missingDensities = new ArrayList<>();
            for (String density : presentDensities) {
                if (!foundDensities.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty()) {
                // Find a representative file to report the issue on
                File resDir = context.getProject().getDir();
                // Report the issue at the project level
                String message = String.format(
                        "The icon `%s` is missing from the following densities: %s",
                        iconName,
                        missingDensities.toString());

                context.report(
                        new Incident(
                                ISSUE,
                                message,
                                context.getProject().getDir(),
                                null,
                                null));
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
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to process all drawable resource files
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Process XML drawable files
        processFile(context);
    }

    private void processFile(@NonNull XmlContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        String density = getDensityFromFolder(folderName);
        if (density == null) {
            return;
        }

        densityFolders.add(density);

        String iconName = getIconName(file);
        if (iconName != null) {
            Set<String> densities = iconDensityMap.computeIfAbsent(iconName, k -> new HashSet<>());
            densities.add(density);
        }
    }

    /**
     * Extracts the density qualifier from a resource folder name.
     * e.g., "drawable-hdpi" -> "hdpi", "mipmap-xhdpi" -> "xhdpi"
     */
    private static String getDensityFromFolder(String folderName) {
        if (folderName == null) {
            return null;
        }

        // Check for density qualifiers
        String[] densities = {DENSITY_LDPI, DENSITY_MDPI, DENSITY_HDPI, DENSITY_XHDPI,
                DENSITY_XXHDPI, "xxxhdpi", "tvdpi"};

        for (String density : densities) {
            if (folderName.contains("-" + density)
                    || folderName.equals("drawable-" + density)
                    || folderName.equals("mipmap-" + density)) {
                return density;
            }
        }

        return null;
    }

    /**
     * Gets the icon name (without extension) from a file.
     */
    private static String getIconName(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }
        return name;
    }

    // XmlScanner interface - we need to handle resource files
    // Since we're scanning resource files, we override the file visiting approach

    /**
     * Called when visiting a resource file. We use this to track icon density coverage.
     */
    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        processFile(context);
    }
}