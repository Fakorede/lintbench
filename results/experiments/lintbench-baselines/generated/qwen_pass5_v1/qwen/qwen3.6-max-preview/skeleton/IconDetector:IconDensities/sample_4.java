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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                            + "current density usage, see https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final List<String> REQUIRED_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global state required; analysis is performed per-project in afterCheckEachProject
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<File> resourceFolders = context.getProject() != null
                ? context.getProject().getResourceFolders()
                : Collections.emptyList();

        Map<String, Set<String>> iconDensities = new HashMap<>();
        Map<String, File> iconLocations = new HashMap<>();

        for (File resDir : resourceFolders) {
            if (!resDir.isDirectory()) continue;
            File[] drawables = resDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("drawable"));
            if (drawables == null) continue;

            for (File drawableDir : drawables) {
                String density = getDensity(drawableDir.getName());
                if (density == null) continue;

                File[] files = drawableDir.listFiles();
                if (files == null) continue;

                for (File file : files) {
                    String name = file.getName();
                    int dot = name.lastIndexOf('.');
                    String baseName = dot > 0 ? name.substring(0, dot) : name;
                    iconDensities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
                    iconLocations.putIfAbsent(baseName, file);
                }
            }
        }

        List<String> required = new ArrayList<>(REQUIRED_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            required.add("ldpi");
        }

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            Set<String> found = entry.getValue();
            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!found.contains(req)) {
                    missing.add(req);
                }
            }
            if (!missing.isEmpty()) {
                File location = iconLocations.get(entry.getKey());
                context.report(ISSUE, Location.create(location),
                        "Missing density variations for icon `" + entry.getKey() + "`: " + String.join(", ", missing));
            }
        }
    }

    private static String getDensity(String folderName) {
        int dash = folderName.indexOf('-');
        if (dash == -1) return null;
        String qualifier = folderName.substring(dash + 1);
        int nextDash = qualifier.indexOf('-');
        if (nextDash != -1) qualifier = qualifier.substring(0, nextDash);
        return qualifier;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
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
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not used for this check
            }
        };
    }
}