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

    // Standard density folders (excluding ldpi by default)
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

    // Map from icon name to set of density folders in which it appears
    private Map<String, Set<String>> iconFolderMap;
    // Track which density folders actually exist in the project
    private Set<String> existingDensityFolders;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconFolderMap = new HashMap<>();
        existingDensityFolders = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (iconFolderMap == null || iconFolderMap.isEmpty()) {
            return;
        }

        boolean includeLdpi = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        String[] densityFolders = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        // Only check densities that actually exist in the project
        List<String> relevantDensities = new ArrayList<>();
        for (String density : densityFolders) {
            if (existingDensityFolders.contains(density)) {
                relevantDensities.add(density);
            }
        }

        if (relevantDensities.size() < 2) {
            // Not enough density folders to warrant a check
            return;
        }

        for (Map.Entry<String, Set<String>> entry : iconFolderMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentFolders = entry.getValue();

            List<String> missingDensities = new ArrayList<>();
            for (String density : relevantDensities) {
                if (!presentFolders.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty() && missingDensities.size() < relevantDensities.size()) {
                // Find the file location to report against (use any existing occurrence)
                // We'll report at the project level since we don't have a specific file here
                String message =
                        String.format(
                                "The icon `%1$s` does not have a drawable for the following "
                                        + "density configuration(s): %2$s",
                                iconName, formatDensities(missingDensities));

                context.report(ISSUE, context.getProject().getDir(), message);
            }
        }
    }

    private static String formatDensities(List<String> densities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < densities.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            // Convert "drawable-mdpi" to "mdpi"
            String density = densities.get(i);
            int dash = density.lastIndexOf('-');
            if (dash >= 0) {
                sb.append(density.substring(dash + 1));
            } else {
                sb.append(density);
            }
        }
        return sb.toString();
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
        // We care about any XML file in drawable folders, but we mainly track via
        // visitElement. Return null to not filter by element type specifically,
        // but we handle tracking in beforeCheckFile.
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Track XML drawable files
        trackIconFile(context.file);
    }

    /**
     * Track an icon file: record the density folder it belongs to.
     */
    private void trackIconFile(File file) {
        if (file == null || iconFolderMap == null) {
            return;
        }

        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String folderName = parentFolder.getName();
        if (!isDensityFolder(folderName)) {
            return;
        }

        existingDensityFolders.add(folderName);

        String iconName = file.getName();
        Set<String> folders = iconFolderMap.get(iconName);
        if (folders == null) {
            folders = new HashSet<>();
            iconFolderMap.put(iconName, folders);
        }
        folders.add(folderName);
    }

    private static boolean isDensityFolder(String folderName) {
        if (folderName == null) {
            return false;
        }
        // Check for drawable-<density> or mipmap-<density> patterns
        return folderName.equals("drawable-ldpi")
                || folderName.equals("drawable-mdpi")
                || folderName.equals("drawable-hdpi")
                || folderName.equals("drawable-xhdpi")
                || folderName.equals("drawable-xxhdpi")
                || folderName.equals("drawable-xxxhdpi")
                || folderName.equals("mipmap-ldpi")
                || folderName.equals("mipmap-mdpi")
                || folderName.equals("mipmap-hdpi")
                || folderName.equals("mipmap-xhdpi")
                || folderName.equals("mipmap-xxhdpi")
                || folderName.equals("mipmap-xxxhdpi");
    }

    // SourceCodeScanner methods - not used but required by skeleton
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
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }
}