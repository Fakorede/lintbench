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
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
                    "Icons will look best if a custom version is provided for each of the major "
                            + "screen density classes (medium, high, extra high, extra extra high). "
                            + "This lint check identifies icons which do not have complete coverage "
                            + "across the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards.",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, IconData>> mDensities = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDensities.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Set<Density> required = getRequiredDensities();
        Project project = context.getProject();
        Map<String, IconData> icons = mDensities.get(project.getDir().getAbsolutePath());
        if (icons == null) {
            return;
        }

        for (Map.Entry<String, IconData> entry : icons.entrySet()) {
            String name = entry.getKey();
            IconData data = entry.getValue();
            if (data.scalable) {
                continue;
            }

            Set<Density> missing = EnumSet.noneOf(Density.class);
            missing.addAll(required);
            missing.removeAll(data.densities);

            if (!missing.isEmpty()) {
                File file = data.getRepresentativeFile();
                Location location =
                        file != null ? Location.create(file) : Location.create(context.getFile());
                String message =
                        String.format(
                                "The icon `%1$s` is missing the following density versions: %2$s",
                                name,
                                formatDensities(missing));
                context.report(new Incident(ISSUE, location, message));
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
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element != context.getDocument().getDocumentElement()) {
            return;
        }

        File file = context.getFile();
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        Density density = getDensityFromFolder(folderName);
        if (density == Density.TV || density == Density.NONE) {
            return;
        }

        String iconName = getBaseName(file.getName());
        String projectKey = context.getProject().getDir().getAbsolutePath();

        Map<String, IconData> icons = mDensities.get(projectKey);
        if (icons == null) {
            icons = new HashMap<>();
            mDensities.put(projectKey, icons);
        }

        IconData data = icons.get(iconName);
        if (data == null) {
            data = new IconData();
            icons.put(iconName, data);
        }

        data.files.add(file);
        if (density == Density.ANY) {
            data.scalable = true;
        } else {
            data.densities.add(density);
        }

        if (isVector(element)) {
            data.scalable = true;
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level Java checks are required for icon density validation.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.<Class<? extends UElement>>asList(
                UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No method checks are required for icon density validation.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No call expression checks are required for icon density validation.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No reference checks are required for icon density validation.
            }
        };
    }

    private static class IconData {
        final Set<Density> densities = EnumSet.noneOf(Density.class);
        final List<File> files = new ArrayList<>();
        boolean scalable;

        File getRepresentativeFile() {
            return files.isEmpty() ? null : files.get(0);
        }
    }

    @NonNull
    private static Set<Density> getRequiredDensities() {
        EnumSet<Density> densities =
                EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH);
        if (Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            densities.add(Density.LOW);
        }
        return densities;
    }

    private static boolean isVector(@NonNull Element element) {
        String tag = element.getTagName();
        return "vector".equals(tag) || "animated-vector".equals(tag);
    }

    @NonNull
    private static Density getDensityFromFolder(@NonNull String folderName) {
        int index = folderName.indexOf('-');
        if (index == -1) {
            return Density.MEDIUM;
        }

        String[] parts = folderName.substring(index + 1).split("-");
        for (String part : parts) {
            Density density = Density.getByString(part);
            if (density != null) {
                return density;
            }
        }

        return Density.MEDIUM;
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int index = fileName.lastIndexOf('.');
        return index == -1 ? fileName : fileName.substring(0, index);
    }

    @NonNull
    private static String formatDensities(@NonNull Set<Density> densities) {
        List<String> names = new ArrayList<>(densities.size());
        for (Density density : densities) {
            switch (density) {
                case LOW:
                    names.add("ldpi");
                    break;
                case MEDIUM:
                    names.add("mdpi");
                    break;
                case HIGH:
                    names.add("hdpi");
                    break;
                case XHIGH:
                    names.add("xhdpi");
                    break;
                case XXHIGH:
                    names.add("xxhdpi");
                    break;
                case XXXHIGH:
                    names.add("xxxhdpi");
                    break;
                case TV:
                    names.add("tvdpi");
                    break;
                default:
                    names.add(density.name().toLowerCase(Locale.US));
                    break;
            }
        }
        return names.toString();
    }
}