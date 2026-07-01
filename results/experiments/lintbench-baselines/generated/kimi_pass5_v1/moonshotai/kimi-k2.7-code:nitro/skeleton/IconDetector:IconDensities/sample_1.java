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
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

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
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, EnumSet<Density>> mDensities;
    private Map<String, File> mRepresentativeFiles;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDensities = new HashMap<>();
        mRepresentativeFiles = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mDensities == null) {
            return;
        }

        scanProject(context);

        boolean includeLdpi = Boolean.parseBoolean(System.getenv(INCLUDE_LDPI));
        EnumSet<Density> required = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH);
        if (includeLdpi) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, EnumSet<Density>> entry : mDensities.entrySet()) {
            String name = entry.getKey();
            EnumSet<Density> present = entry.getValue();

            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);

            if (missing.isEmpty()) {
                continue;
            }

            File file = mRepresentativeFiles.get(name);
            if (file == null) {
                continue;
            }

            StringBuilder missingNames = new StringBuilder();
            for (Density density : missing) {
                if (missingNames.length() > 0) {
                    missingNames.append(", ");
                }
                missingNames.append(density.getName());
            }

            String message =
                    String.format(
                            "Missing density variation: the icon `%1$s` is missing density variations for %2$s",
                            name, missingNames.toString());

            Incident incident = new Incident(ISSUE, Location.create(file), message);
            context.report(incident);
        }

        mDensities.clear();
        mRepresentativeFiles.clear();
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
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
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
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
            }
        };
    }

    private void scanProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        for (File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                String folderName = folder.getName();
                if (!folderName.startsWith("drawable")) {
                    continue;
                }

                Density density = parseDensity(folderName);
                if (density == null) {
                    continue;
                }

                String densityName = density.getName();
                if ("nodpi".equals(densityName)
                        || "tvdpi".equals(densityName)
                        || "anydpi".equals(densityName)) {
                    continue;
                }

                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String fileName = file.getName();
                    if (fileName.endsWith(".xml")) {
                        continue;
                    }

                    String key = fileName;
                    EnumSet<Density> densities =
                            mDensities.computeIfAbsent(
                                    key, k -> EnumSet.noneOf(Density.class));
                    densities.add(density);

                    mRepresentativeFiles.putIfAbsent(key, file);
                }
            }
        }
    }

    private static Density parseDensity(String folderName) {
        if (!folderName.startsWith("drawable")) {
            return null;
        }

        if ("drawable".equals(folderName)) {
            return Density.MEDIUM;
        }

        String qualifiers = folderName.substring("drawable-".length());
        for (String qualifier : qualifiers.split("-")) {
            Density density = Density.getEnum(qualifier);
            if (density != null) {
                return density;
            }
        }
        return null;
    }
}