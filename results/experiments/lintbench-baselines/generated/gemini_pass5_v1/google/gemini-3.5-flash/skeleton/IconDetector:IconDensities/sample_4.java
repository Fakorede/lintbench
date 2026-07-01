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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Pattern DENSITY_PATTERN = Pattern.compile("-([l|m|h|x]+dpi)(?:-|$)");

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        Map<String, Map<String, File>> iconToDensityMap = new HashMap<>();

        for (File resFolder : resourceFolders) {
            File[] subFolders = resFolder.listFiles();
            if (subFolders == null) {
                continue;
            }
            for (File subFolder : subFolders) {
                if (!subFolder.isDirectory()) {
                    continue;
                }
                String folderName = subFolder.getName();
                if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                    Matcher matcher = DENSITY_PATTERN.matcher(folderName);
                    if (matcher.find()) {
                        String density = matcher.group(1);
                        File[] files = subFolder.listFiles();
                        if (files == null) {
                            continue;
                        }
                        for (File file : files) {
                            if (file.isFile()) {
                                String filename = file.getName().toLowerCase();
                                if (filename.endsWith(".png")
                                        || filename.endsWith(".jpg")
                                        || filename.endsWith(".jpeg")
                                        || filename.endsWith(".gif")
                                        || filename.endsWith(".webp")) {
                                    int dot = file.getName().lastIndexOf('.');
                                    String baseName = dot != -1 ? file.getName().substring(0, dot) : file.getName();

                                    Map<String, File> densityMap = iconToDensityMap.get(baseName);
                                    if (densityMap == null) {
                                        densityMap = new HashMap<>();
                                        iconToDensityMap.put(baseName, densityMap);
                                    }
                                    densityMap.put(density, file);
                                }
                            }
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        List<String> baseExpectedDensities = new ArrayList<>();
        if (includeLdpi) {
            baseExpectedDensities.add("ldpi");
        }
        baseExpectedDensities.add("mdpi");
        baseExpectedDensities.add("hdpi");
        baseExpectedDensities.add("xhdpi");
        baseExpectedDensities.add("xxhdpi");

        for (Map.Entry<String, Map<String, File>> entry : iconToDensityMap.entrySet()) {
            String baseName = entry.getKey();
            Map<String, File> densityMap = entry.getValue();

            boolean isLauncher = baseName.startsWith("ic_launcher");
            if (!isLauncher) {
                for (File file : densityMap.values()) {
                    if (file.getParentFile().getName().startsWith("mipmap")) {
                        isLauncher = true;
                        break;
                    }
                }
            }

            List<String> expected = new ArrayList<>(baseExpectedDensities);
            if (isLauncher) {
                expected.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String expectedDensity : expected) {
                if (!densityMap.containsKey(expectedDensity)) {
                    missing.add(expectedDensity);
                }
            }

            if (!missing.isEmpty()) {
                File firstFile = densityMap.values().iterator().next();
                String message = "Missing the following densities: " + String.join(", ", missing);
                Incident incident = new Incident(ISSUE, context.getLocation(firstFile), message);
                context.report(incident);
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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No-op
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op
            }
        };
    }
}