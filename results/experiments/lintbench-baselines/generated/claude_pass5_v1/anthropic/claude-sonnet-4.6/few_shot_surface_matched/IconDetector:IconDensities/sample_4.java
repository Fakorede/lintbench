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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String INCLUDE_LDPI_ENV = "ANDROID_LINT_INCLUDE_LDPI";

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

    public static final Issue ICON_DENSITIES =
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
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    /** Map from icon name to set of density folders it appears in, per project */
    private final Map<Project, Map<String, Set<String>>> mProjectIcons = new HashMap<>();

    public IconDetector() {}

    // ---- Detector overrides ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mProjectIcons.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        Map<String, Set<String>> iconMap = mProjectIcons.get(project);
        if (iconMap == null) {
            return;
        }

        boolean includeLdpi = false;
        String ldpiEnv = System.getenv(INCLUDE_LDPI_ENV);
        if (ldpiEnv != null && (ldpiEnv.equalsIgnoreCase("true") || ldpiEnv.equals("1"))) {
            includeLdpi = true;
        }

        String[] expectedFolders = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        for (Map.Entry<String, Set<String>> entry : iconMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentFolders = entry.getValue();

            List<String> missingFolders = new java.util.ArrayList<>();
            for (String folder : expectedFolders) {
                if (!presentFolders.contains(folder)) {
                    missingFolders.add(folder);
                }
            }

            if (!missingFolders.isEmpty() && !missingFolders.equals(
                    Arrays.asList(expectedFolders))) {
                // Only report if at least one density folder exists (icon is partially covered)
                if (presentFolders.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("The icon `").append(iconName).append("` is missing density variants");
                    sb.append(" in: ");
                    for (int i = 0; i < missingFolders.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(missingFolders.get(i));
                    }

                    // Find a file to attach the location to
                    File resDir = null;
                    List<File> resourceFolders = project.getResourceFolders();
                    if (!resourceFolders.isEmpty()) {
                        resDir = resourceFolders.get(0);
                    }

                    Location location = Location.create(project.getDir());
                    if (resDir != null) {
                        // Try to find an existing icon file for location
                        for (String folder : presentFolders) {
                            File iconFile = new File(new File(resDir, folder), iconName);
                            if (iconFile.exists()) {
                                location = Location.create(iconFile);
                                break;
                            }
                        }
                    }

                    Incident incident = new Incident(ICON_DENSITIES, location, sb.toString());
                    context.report(incident);
                }
            }
        }

        mProjectIcons.remove(project);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    // ---- XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        recordIconFromContext(context);
    }

    private void recordIconFromContext(@NonNull XmlContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }
        recordIcon(context.getProject(), file);
    }

    private void recordIcon(@NonNull Project project, @NonNull File file) {
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }
        String folderName = parentDir.getName();
        if (!isDensityFolder(folderName)) {
            return;
        }

        String iconName = file.getName();

        Map<String, Set<String>> iconMap =
                mProjectIcons.computeIfAbsent(project, k -> new HashMap<>());
        iconMap.computeIfAbsent(iconName, k -> new HashSet<>()).add(folderName);
    }

    private boolean isDensityFolder(@NonNull String folderName) {
        return folderName.startsWith("drawable-") || folderName.startsWith("mipmap-");
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastHandler createUastHandler(
            @NonNull JavaContext context) {
        return new IconUastHandler(context);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op: handled via createUastHandler
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // No-op: handled via createUastHandler
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // No-op: handled via createUastHandler
    }

    private static class IconUastHandler extends com.android.tools.lint.detector.api.UastHandler {
        private final JavaContext mContext;

        IconUastHandler(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            return false;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            return false;
        }
    }
}