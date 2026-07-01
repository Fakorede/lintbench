package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
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
                    "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n" +
                    "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, Set<Density>> iconDensities;
    private Map<String, Location> iconLocations;
    private Set<Density> requiredDensities;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities = new HashMap<>();
        iconLocations = new HashMap<>();
        requiredDensities = EnumSet.of(
                Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
        if (Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            requiredDensities.add(Density.LOW);
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Set<Density>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<Density> found = entry.getValue();
            Set<Density> missing = EnumSet.copyOf(requiredDensities);
            missing.removeAll(found);

            if (!missing.isEmpty()) {
                List<String> missingNames = new ArrayList<>();
                for (Density d : missing) {
                    missingNames.add(d.getResourceValue());
                }
                Collections.sort(missingNames);
                String message = String.format(
                        "Missing the following drawables for icon `%s`: %s",
                        iconName, String.join(", ", missingNames));
                Location location = iconLocations.get(iconName);
                if (location != null) {
                    context.report(ISSUE, location, message);
                }
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
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "bitmap", "layer-list", "selector", "shape",
                "ripple", "inset", "clip", "scale", "rotate", "animated-vector",
                "animation-list", "level-list", "transition", "adaptive-icon", "nine-patch");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element != element.getOwnerDocument().getDocumentElement()) {
            return;
        }
        if (context.getFolderConfiguration() == null ||
                context.getFolderConfiguration().getDensityQualifier() == null) {
            return;
        }
        Density density = context.getFolderConfiguration().getDensityQualifier().getValue();
        if (density == null || !requiredDensities.contains(density)) {
            return;
        }
        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        iconDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
        iconLocations.putIfAbsent(baseName, context.getLocation(element));
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
                // Not applicable for icon density checks
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not applicable for icon density checks
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not applicable for icon density checks
            }
        };
    }
}