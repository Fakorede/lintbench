package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.FolderConfiguration;
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
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, EnumSet<Density>> iconDensities = new HashMap<>();
    private final Map<String, Location> iconLocations = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities.clear();
        iconLocations.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        EnumSet<Density> required = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
        if (includeLdpi) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, EnumSet<Density>> entry : iconDensities.entrySet()) {
            String name = entry.getKey();
            EnumSet<Density> found = entry.getValue();
            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(found);
            if (!missing.isEmpty()) {
                Location location = iconLocations.get(name);
                String missingStr = missing.stream()
                        .map(d -> "drawable-" + d.getResourceValue() + "/" + name + ".png")
                        .collect(Collectors.joining(", "));
                String message = "Missing the following drawables in res: " + missingStr;
                context.report(ISSUE, location, message);
            }
        }
        iconDensities.clear();
        iconLocations.clear();
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() != null) {
            return;
        }
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            return;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return;
        String baseName = fileName.substring(0, dotIndex);

        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null) return;
        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) return;
        Density density = densityQualifier.getValue();
        if (density == null) return;

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        EnumSet<Density> relevant = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
        if (includeLdpi) relevant.add(Density.LOW);

        if (relevant.contains(density)) {
            iconDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
            iconLocations.putIfAbsent(baseName, context.getLocation(element));
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not applicable for resource density check
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