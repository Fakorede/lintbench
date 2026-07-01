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
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final boolean INCLUDE_LDPI;

    static {
        String env = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        INCLUDE_LDPI = "true".equals(env);
    }

    private static final String[] DENSITY_QUALIFIERS;

    static {
        if (INCLUDE_LDPI) {
            DENSITY_QUALIFIERS = new String[]{"ldpi", "mdpi", "hdpi", "xhdpi"};
        } else {
            DENSITY_QUALIFIERS = new String[]{"mdpi", "hdpi", "xhdpi"};
        }
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    // Map from icon name to set of densities it has been found in
    private Map<String, Set<String>> mIconsPerDensity;
    // Set of all icon names found in any density folder
    private Set<String> mAllIcons;

    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

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
        File res = new File(project.getDir(), "res");
        if (!res.exists()) {
            return;
        }

        // Collect icons from drawable-*dpi folders
        File[] resDirs = res.listFiles();
        if (resDirs == null) {
            return;
        }

        // Reset and re-scan from file system
        Map<String, Set<String>> iconDensities = new HashMap<>();

        for (File dir : resDirs) {
            String dirName = dir.getName();
            if (!dir.isDirectory()) {
                continue;
            }
            String density = getDensityFromFolder(dirName);
            if (density == null) {
                continue;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }

            for (File file : files) {
                String name = file.getName();
                if (isIconFile(name)) {
                    String iconName = name;
                    Set<String> densities = iconDensities.get(iconName);
                    if (densities == null) {
                        densities = new HashSet<>();
                        iconDensities.put(iconName, densities);
                    }
                    densities.add(density);
                }
            }
        }

        // Check for missing densities
        List<String> requiredDensities = Arrays.asList(DENSITY_QUALIFIERS);

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> foundDensities = entry.getValue();

            List<String> missing = new java.util.ArrayList<>();
            for (String density : requiredDensities) {
                if (!foundDensities.contains(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Icon `").append(iconName).append("` is missing density variants: ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missing.get(i));
                }

                // Find a file to attach the location to
                File locationFile = findIconFile(res, iconName, foundDensities);
                Location location = locationFile != null
                        ? Location.create(locationFile)
                        : Location.create(res);

                Incident incident = new Incident(
                        ICON_DENSITIES,
                        sb.toString(),
                        location);
                context.report(incident);
            }
        }
    }

    private static File findIconFile(File res, String iconName, Set<String> densities) {
        for (String density : densities) {
            File dir = new File(res, "drawable-" + density);
            File file = new File(dir, iconName);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private static String getDensityFromFolder(String dirName) {
        if (!dirName.startsWith("drawable")) {
            return null;
        }
        if (dirName.equals("drawable-ldpi")) return "ldpi";
        if (dirName.equals("drawable-mdpi")) return "mdpi";
        if (dirName.equals("drawable-hdpi")) return "hdpi";
        if (dirName.equals("drawable-xhdpi")) return "xhdpi";
        if (dirName.equals("drawable-xxhdpi")) return "xxhdpi";
        if (dirName.equals("drawable-xxxhdpi")) return "xxxhdpi";
        return null;
    }

    private static boolean isIconFile(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }

    // XmlScanner methods

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op for this detector
    }

    // SourceCodeScanner methods

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public org.jetbrains.uast.visitor.UastVisitor createUastHandler(@NonNull JavaContext context) {
        return new AbstractUastVisitor() {
            @Override
            public boolean visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No-op for this detector
                return false;
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op for this detector
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op for this detector
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this detector
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        return false;
    }
}