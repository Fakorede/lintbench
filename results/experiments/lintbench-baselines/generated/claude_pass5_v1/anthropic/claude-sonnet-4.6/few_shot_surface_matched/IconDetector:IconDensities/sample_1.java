package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String DRAWABLE_PREFIX = "drawable";
    private static final String MIPMAP_PREFIX = "mipmap";

    private static final String[] DENSITY_QUALIFIERS = {
        "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    };

    private static final String LDPI_QUALIFIER = "ldpi";

    public static final Issue ICON_DENSITIES =
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
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)))
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    /** Map from icon name to the set of densities it appears in */
    private Map<String, Set<String>> mIconsPerDensity;

    /** All icon names found */
    private Set<String> mAllIcons;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconsPerDensity = new HashMap<>();
        mAllIcons = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mIconsPerDensity == null || mAllIcons == null) {
            return;
        }

        Project project = context.getProject();
        if (!project.isGradleProject() && !project.getReportIssues()) {
            return;
        }

        boolean includeLdpi = false;
        String ldpiEnv = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        if ("true".equals(ldpiEnv)) {
            includeLdpi = true;
        }

        List<String> expectedDensities;
        if (includeLdpi) {
            expectedDensities = Arrays.asList("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        } else {
            expectedDensities = Arrays.asList(DENSITY_QUALIFIERS);
        }

        // For each icon, check if it's missing from any density folder
        for (String iconName : mAllIcons) {
            Set<String> densities = mIconsPerDensity.get(iconName);
            if (densities == null) {
                densities = Collections.emptySet();
            }

            List<String> missingDensities = new java.util.ArrayList<>();
            for (String density : expectedDensities) {
                if (!densities.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty() && densities.size() > 0) {
                // Only report if the icon exists in at least one density folder
                // (not in default drawable/ folder only)
                StringBuilder sb = new StringBuilder();
                sb.append("The icon `").append(iconName).append("` appears to be missing ");
                sb.append("from the following density folders: ");
                for (int i = 0; i < missingDensities.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missingDensities.get(i));
                }

                // Find a resource directory to report against
                File resDir = findResDir(project);
                Location location;
                if (resDir != null) {
                    location = Location.create(resDir);
                } else {
                    location = Location.create(project.getDir());
                }

                Incident incident = new Incident(ICON_DENSITIES, location, sb.toString());
                context.report(incident, new LintMap().put("icon", iconName));
            }
        }
    }

    @Nullable
    private File findResDir(@NonNull Project project) {
        for (File dir : project.getResourceFolders()) {
            if (dir.exists()) {
                return dir;
            }
        }
        return null;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        // Apply to resource directories
        String path = file.getPath();
        return path.contains("res") || path.endsWith(".java") || path.endsWith(".kt");
    }

    // XmlScanner methods

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "nine-patch", "item", "selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Track XML drawable resources
        File file = context.file;
        String folderName = file.getParentFile() != null ? file.getParentFile().getName() : "";
        String densityQualifier = getDensityQualifier(folderName);

        if (densityQualifier != null) {
            String iconName = getBaseName(file.getName());
            if (iconName != null) {
                recordIcon(iconName, densityQualifier);
            }
        }
    }

    // SourceCodeScanner methods

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new UastHandler(context);
    }

    private class UastHandler extends Detector.UastHandler {
        private final JavaContext mContext;

        UastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            // No-op for this detector
        }

        @Override
        public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            // No-op for this detector
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    // Helper methods

    private void recordIcon(@NonNull String iconName, @NonNull String density) {
        if (mAllIcons != null) {
            mAllIcons.add(iconName);
        }
        if (mIconsPerDensity != null) {
            Set<String> densities = mIconsPerDensity.get(iconName);
            if (densities == null) {
                densities = new HashSet<>();
                mIconsPerDensity.put(iconName, densities);
            }
            densities.add(density);
        }
    }

    @Nullable
    private String getDensityQualifier(@NonNull String folderName) {
        if (!folderName.startsWith(DRAWABLE_PREFIX) && !folderName.startsWith(MIPMAP_PREFIX)) {
            return null;
        }

        // Check for density qualifiers
        for (String density : DENSITY_QUALIFIERS) {
            if (folderName.contains("-" + density) || folderName.equals(DRAWABLE_PREFIX + "-" + density)) {
                return density;
            }
        }

        // Check for ldpi
        if (folderName.contains("-" + LDPI_QUALIFIER)) {
            return LDPI_QUALIFIER;
        }

        return null;
    }

    @Nullable
    private String getBaseName(@NonNull String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName.isEmpty() ? null : fileName;
    }

    /**
     * Scan resource files to find icons in density folders.
     * This is called for each resource file in the project.
     */
    public void checkResourceFile(@NonNull Context context, @NonNull File file) {
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        String densityQualifier = getDensityQualifier(folderName);

        if (densityQualifier != null) {
            String name = file.getName();
            if (isIconFile(name)) {
                String iconName = getBaseName(name);
                if (iconName != null) {
                    recordIcon(iconName, densityQualifier);
                }
            }
        }
    }

    private boolean isIconFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".9.png");
    }
}