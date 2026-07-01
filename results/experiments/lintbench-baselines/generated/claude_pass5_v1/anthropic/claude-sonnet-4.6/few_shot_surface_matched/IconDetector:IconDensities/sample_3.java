package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final boolean INCLUDE_LDPI;

    static {
        String includeLdpi = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        INCLUDE_LDPI = "true".equals(includeLdpi);
    }

    /** The standard densities to check */
    private static final String[] DENSITY_QUALIFIERS = new String[]{
            "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    /** All density qualifiers including ldpi */
    private static final String[] ALL_DENSITY_QUALIFIERS = new String[]{
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

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
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    /** Map from icon name to set of densities for which we have the icon */
    private final Map<String, Set<String>> iconDensities = new HashMap<>();

    /** Set of density folders found */
    private final Set<String> densityFolders = new HashSet<>();

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities.clear();
        densityFolders.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.getProject().isGradleProject()
                || context.getProject().isLibrary()) {
            checkIconDensities(context);
        }
    }

    private void checkIconDensities(@NonNull Context context) {
        // Determine which densities are required
        String[] requiredDensities = INCLUDE_LDPI ? ALL_DENSITY_QUALIFIERS : DENSITY_QUALIFIERS;

        // Find icons that are missing from some density folders
        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentDensities = entry.getValue();

            List<String> missingDensities = new ArrayList<>();
            for (String density : requiredDensities) {
                if (densityFolders.contains(density) && !presentDensities.contains(density)) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty()) {
                Collections.sort(missingDensities);
                String message = String.format(
                        "The icon `%1$s` does not have a drawable for the following density "
                                + "configuration%2$s: %3$s",
                        iconName,
                        missingDensities.size() > 1 ? "s" : "",
                        formatDensities(missingDensities));

                Location location = guessLocation(context, iconName, presentDensities);
                Incident incident = new Incident(ICON_DENSITIES, location, message);
                context.report(incident, LintMap.Companion.create()
                        .put("icon", iconName)
                        .put("missing", String.join(",", missingDensities)));
            }
        }
    }

    private Location guessLocation(@NonNull Context context, @NonNull String iconName,
            @NonNull Set<String> presentDensities) {
        // Try to find the file in one of the present densities to use as location
        Project project = context.getProject();
        File resourceDir = project.getResourceFolders().isEmpty()
                ? null
                : project.getResourceFolders().get(0);

        if (resourceDir != null) {
            for (String density : presentDensities) {
                // Try drawable folder
                for (String folderPrefix : new String[]{"drawable", "mipmap"}) {
                    File folder = new File(resourceDir, folderPrefix + "-" + density);
                    if (folder.exists()) {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                String name = file.getName();
                                int dot = name.lastIndexOf('.');
                                String baseName = dot >= 0 ? name.substring(0, dot) : name;
                                if (baseName.equals(iconName)) {
                                    return Location.create(file);
                                }
                            }
                        }
                    }
                }
            }
        }

        return Location.create(project.getDir());
    }

    private static String formatDensities(@NonNull List<String> densities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < densities.size(); i++) {
            if (i > 0) {
                if (i == densities.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append("`").append(densities.get(i)).append("`");
        }
        return sb.toString();
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident,
            @NonNull LintMap map) {
        // Allow all incidents through; filtering can be customized here if needed
        return true;
    }

    // ---- XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_SERVICE, TAG_RECEIVER,
                TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We process the resource files by examining the folder structure
        // through the file system rather than XML visiting for density checks.
        // However, we still need to register the resource folder structure.
        File resourceFile = context.file;
        File folder = resourceFile.getParentFile();
        if (folder != null) {
            String folderName = folder.getName();
            recordDensityFolder(folderName, resourceFile);
        }
    }

    private void recordDensityFolder(@NonNull String folderName, @NonNull File resourceFile) {
        // Parse folder name to extract density qualifier
        String density = extractDensity(folderName);
        if (density != null) {
            densityFolders.add(density);

            // Record the icon name
            String fileName = resourceFile.getName();
            int dot = fileName.lastIndexOf('.');
            String iconName = dot >= 0 ? fileName.substring(0, dot) : fileName;

            Set<String> densities = iconDensities.get(iconName);
            if (densities == null) {
                densities = new HashSet<>();
                iconDensities.put(iconName, densities);
            }
            densities.add(density);
        }
    }

    @Nullable
    private static String extractDensity(@NonNull String folderName) {
        // Folder names are like "drawable-hdpi", "mipmap-xhdpi", etc.
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

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Handle simple name references if needed for icon resource tracking
            }
        };
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context,
            @NonNull UCallExpression expression) {
        // No-op for this detector
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // No-op for this detector
    }
}