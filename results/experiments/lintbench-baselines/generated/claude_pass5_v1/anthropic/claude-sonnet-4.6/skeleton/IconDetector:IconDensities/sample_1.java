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

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                    IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    // Standard density buckets (excluding ldpi by default)
    private static final String[] DENSITY_FOLDERS = {
        "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final String LDPI = "ldpi";
    private static final String ENV_VAR_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    // Map from icon name to set of density folders where it exists
    private final Map<String, Set<String>> mIconsPerDensity = new HashMap<>();
    // Set of all density folders found in the project
    private final Set<String> mDensityFolders = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconsPerDensity.clear();
        mDensityFolders.clear();

        // Scan res directory for drawable-* folders
        File resDir = context.getProject().getResourceFolders().isEmpty()
                ? null
                : context.getProject().getResourceFolders().get(0);

        if (resDir == null || !resDir.exists()) {
            return;
        }

        boolean includeLdpi = includeLdpi();

        File[] children = resDir.listFiles();
        if (children == null) {
            return;
        }

        for (File folder : children) {
            if (!folder.isDirectory()) {
                continue;
            }
            String name = folder.getName();
            if (!name.startsWith("drawable")) {
                continue;
            }

            String density = getDensityFromFolder(name);
            if (density == null) {
                continue;
            }

            if (!includeLdpi && LDPI.equals(density)) {
                continue;
            }

            mDensityFolders.add(density);

            File[] icons = folder.listFiles();
            if (icons == null) {
                continue;
            }

            for (File icon : icons) {
                if (!icon.isFile()) {
                    continue;
                }
                String iconName = icon.getName();
                if (isImageFile(iconName)) {
                    Set<String> densities = mIconsPerDensity.computeIfAbsent(
                            iconName, k -> new HashSet<>());
                    densities.add(density);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mDensityFolders.size() < 2) {
            // Not enough density folders to compare
            return;
        }

        for (Map.Entry<String, Set<String>> entry : mIconsPerDensity.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densitiesWithIcon = entry.getValue();

            // Find missing densities
            List<String> missingDensities = new ArrayList<>();
            for (String density : mDensityFolders) {
                if (!densitiesWithIcon.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty()) {
                Collections.sort(missingDensities);
                String message = String.format(
                        "The icon `%1$s` is missing from the following density folders: %2$s",
                        iconName, formatDensityList(missingDensities));

                context.report(
                        new Incident(
                                ISSUE,
                                message,
                                context.getProject().getDir(),
                                null));
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We don't need to scan specific XML elements for this check
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this check
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this check
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        // We don't need to scan UAST elements for this check
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for this check
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this check
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this check
            }
        };
    }

    // ---- Helper methods ----

    /**
     * Extracts the density qualifier from a drawable folder name.
     * e.g. "drawable-hdpi" -> "hdpi", "drawable-xhdpi-v4" -> "xhdpi"
     */
    private static String getDensityFromFolder(String folderName) {
        if (!folderName.startsWith("drawable")) {
            return null;
        }

        // Strip "drawable" prefix
        String rest = folderName.substring("drawable".length());
        if (rest.isEmpty()) {
            // plain "drawable" folder - no density
            return null;
        }

        if (!rest.startsWith("-")) {
            return null;
        }

        // Split qualifiers by '-'
        String[] qualifiers = rest.substring(1).split("-");
        for (String qualifier : qualifiers) {
            if (isDensityQualifier(qualifier)) {
                return qualifier;
            }
        }

        return null;
    }

    private static boolean isDensityQualifier(String qualifier) {
        switch (qualifier) {
            case "ldpi":
            case "mdpi":
            case "hdpi":
            case "xhdpi":
            case "xxhdpi":
            case "xxxhdpi":
                return true;
            default:
                return false;
        }
    }

    private static boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private static boolean includeLdpi() {
        String env = System.getenv(ENV_VAR_INCLUDE_LDPI);
        return "true".equalsIgnoreCase(env);
    }

    private static String formatDensityList(List<String> densities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < densities.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(densities.get(i));
        }
        return sb.toString();
    }
}