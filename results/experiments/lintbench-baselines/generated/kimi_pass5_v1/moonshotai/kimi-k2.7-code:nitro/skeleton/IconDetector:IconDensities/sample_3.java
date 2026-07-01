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
import java.io.File;
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

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the major "
                            + "screen density classes (low, medium, high, extra high). This lint "
                            + "check identifies icons which do not have complete coverage across "
                            + "the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Set<Density> REQUIRED_DENSITIES =
            EnumSet.of(Density.MDPI, Density.HDPI, Density.XHDPI);

    private final Map<String, Set<Density>> mDensities = new HashMap<>();
    private final Map<String, Location> mLocations = new HashMap<>();
    private boolean mIncludeLdpi;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDensities.clear();
        mLocations.clear();
        String value = System.getenv(INCLUDE_LDPI);
        mIncludeLdpi = value != null && value.equalsIgnoreCase("true");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // XML drawables (e.g. vector assets) are density-independent; bitmap files are inspected
        // in beforeCheckFile.
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(parent.getName());
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String name = file.getName();
        if (name.endsWith(".xml")) {
            return;
        }
        if (!isImageFile(name)) {
            return;
        }

        Density density = getDensity(parent.getName());
        if (density == null || density == Density.NODPI) {
            return;
        }

        String baseName = getBaseName(name);
        mDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
        mLocations.putIfAbsent(baseName, Location.create(file));
    }

    private static boolean isImageFile(@NonNull String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static Density getDensity(@NonNull String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            Density density = parseDensity(parts[i]);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    private static Density parseDensity(@NonNull String qualifier) {
        switch (qualifier) {
            case "ldpi":
                return Density.LDPI;
            case "mdpi":
                return Density.MDPI;
            case "tvdpi":
                return Density.TVDPI;
            case "hdpi":
                return Density.HDPI;
            case "xhdpi":
                return Density.XHDPI;
            case "xxhdpi":
                return Density.XXHDPI;
            case "xxxhdpi":
                return Density.XXXHDPI;
            case "nodpi":
                return Density.NODPI;
            default:
                return null;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        EnumSet<Density> required = EnumSet.copyOf(REQUIRED_DENSITIES);
        if (mIncludeLdpi) {
            required.add(Density.LDPI);
        }

        for (Map.Entry<String, Set<Density>> entry : mDensities.entrySet()) {
            Set<Density> present = entry.getValue();
            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);
            if (missing.isEmpty()) {
                continue;
            }

            Location location = mLocations.get(entry.getKey());
            if (location == null) {
                continue;
            }

            String message =
                    String.format(
                            "Missing density variation%s for `%s` (%s)",
                            missing.size() == 1 ? "" : "s",
                            entry.getKey(),
                            formatDensities(missing));

            context.report(ISSUE, location, message);
        }
    }

    private static String formatDensities(@NonNull Set<Density> densities) {
        StringBuilder sb = new StringBuilder();
        for (Density density : densities) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(density.getName());
        }
        return sb.toString();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Low-density filtering is applied while reporting; keep all remaining incidents.
        return true;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used by this check.
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
                // Not used by this check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used by this check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used by this check.
            }
        };
    }
}