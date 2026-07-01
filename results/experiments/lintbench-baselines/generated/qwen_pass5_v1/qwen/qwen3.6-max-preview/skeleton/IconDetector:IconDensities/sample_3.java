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
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
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
import java.util.stream.Collectors;
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
                    "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n" +
                    "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, Set<String>> iconDensities;
    private Map<String, File> iconLocations;
    private Set<String> requiredDensities;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities = new HashMap<>();
        iconLocations = new HashMap<>();
        requiredDensities = new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"));
        if (Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            requiredDensities.add("ldpi");
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (iconDensities == null || requiredDensities == null) return;
        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> found = entry.getValue();
            Set<String> missing = new HashSet<>(requiredDensities);
            missing.removeAll(found);
            if (!missing.isEmpty() && !found.isEmpty()) {
                File locationFile = iconLocations.get(iconName);
                Location location = locationFile != null ? Location.create(locationFile) : Location.create(context.project.getDir());
                String missingList = missing.stream()
                        .map(d -> d + "/" + iconName + ".png")
                        .sorted()
                        .collect(Collectors.joining(", "));
                String message = String.format("Missing the following drawables for `%s`: %s", iconName, missingList);
                context.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() != null) return;
        File file = context.file;
        File parentDir = file.getParentFile();
        if (parentDir == null) return;

        String parentName = parentDir.getName();
        if (parentName.startsWith("drawable-")) {
            String density = parentName.substring("drawable-".length());
            int dash = density.indexOf('-');
            if (dash != -1) density = density.substring(0, dash);

            if (requiredDensities != null && (requiredDensities.contains(density) || density.equals("ldpi"))) {
                String fileName = file.getName();
                int dot = fileName.lastIndexOf('.');
                String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;

                iconDensities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
                iconLocations.putIfAbsent(baseName, file);
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not applicable for icon density checks
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
                // Not applicable
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not applicable
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not applicable
            }
        };
    }
}