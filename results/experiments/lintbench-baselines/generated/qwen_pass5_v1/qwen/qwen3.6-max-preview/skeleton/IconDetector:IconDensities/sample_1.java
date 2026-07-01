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
                    "Icons will look best if a custom version is provided for each of the " +
                    "major screen density classes (low, medium, high, extra high). " +
                    "This lint check identifies icons which do not have complete coverage " +
                    "across the densities.\n\n" +
                    "Low density is not really used much anymore, so this check ignores " +
                    "the ldpi density. To force lint to include it, set the environment " +
                    "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
                    "current density usage, see " +
                    "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Set<Density>> iconDensities = new HashMap<>();
    private final Map<String, Location> iconLocations = new HashMap<>();
    private static final boolean INCLUDE_LDPI = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensities.clear();
        iconLocations.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Set<Density> required = EnumSet.of(Density.MDPI, Density.HDPI, Density.XHDPI, Density.XXHDPI, Density.XXXHDPI);
        if (INCLUDE_LDPI) {
            required.add(Density.LDPI);
        }

        for (Map.Entry<String, Set<Density>> entry : iconDensities.entrySet()) {
            String baseName = entry.getKey();
            Set<Density> found = entry.getValue();
            Set<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(found);

            if (!missing.isEmpty()) {
                Location location = iconLocations.get(baseName);
                StringBuilder sb = new StringBuilder("Missing densities for icon ");
                sb.append(baseName).append(": ");
                boolean first = true;
                for (Density d : missing) {
                    if (!first) sb.append(", ");
                    sb.append(d.getResourceValue());
                    first = false;
                }
                context.report(ISSUE, location, sb.toString());
            }
        }
        iconDensities.clear();
        iconLocations.clear();
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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only process the root element of each resource file
        if (element.getParentNode() instanceof Element) {
            return;
        }
        String fileName = context.file.getName();
        String baseName = getBaseName(fileName);
        if (context.getConfiguration() != null && context.getConfiguration().getDensityQualifier() != null) {
            Density density = context.getConfiguration().getDensityQualifier().getValue();
            if (density != null && density != Density.NODPI && density != Density.ANYDPI) {
                iconDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
                iconLocations.putIfAbsent(baseName, context.getLocation(element));
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

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}