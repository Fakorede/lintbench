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
                            + "across the densities. Low density is not really used much anymore, "
                            + "so this check ignores the ldpi density. To force lint to include it, "
                            + "set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`.",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (context.getProject() == null) {
            return;
        }
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        Map<String, Set<String>> iconDensities = new HashMap<>();
        Map<String, File> iconFiles = new HashMap<>();

        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Set<String> requiredDensities = new HashSet<>();
        if (includeLdpi) {
            requiredDensities.add("ldpi");
        }
        requiredDensities.add("mdpi");
        requiredDensities.add("hdpi");
        requiredDensities.add("xhdpi");
        requiredDensities.add("xxhdpi");

        for (File resFolder : resourceFolders) {
            File[] subdirs = resFolder.listFiles();
            if (subdirs == null) {
                continue;
            }
            for (File subdir : subdirs) {
                if (!subdir.isDirectory()) {
                    continue;
                }
                String name = subdir.getName();
                if (!name.startsWith("drawable")) {
                    continue;
                }
                String density = getDensity(name);
                if (density == null) {
                    continue;
                }

                File[] files = subdir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String fileName = file.getName();
                    String lower = fileName.toLowerCase();
                    if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            || lower.endsWith(".gif") || lower.endsWith(".webp"))) {
                        continue;
                    }

                    int dot = fileName.lastIndexOf('.');
                    String baseName = dot != -1 ? fileName.substring(0, dot) : fileName;

                    Set<String> densities = iconDensities.get(baseName);
                    if (densities == null) {
                        densities = new HashSet<>();
                        iconDensities.put(baseName, densities);
                    }
                    densities.add(density);
                    if (!iconFiles.containsKey(baseName)) {
                        iconFiles.put(baseName, file);
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densities = entry.getValue();

            boolean hasAnyDensity = false;
            for (String d : densities) {
                if (requiredDensities.contains(d) || "xxxhdpi".equals(d)) {
                    hasAnyDensity = true;
                    break;
                }
            }
            if (!hasAnyDensity) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String req : requiredDensities) {
                if (!densities.contains(req)) {
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                File file = iconFiles.get(iconName);
                if (file != null) {
                    Collections.sort(missing);
                    String missingStr = String.join(", ", missing);
                    String message = String.format("Missing the following densities in drawable directories: %s", missingStr);
                    Incident incident = new Incident(ISSUE, message, context.getLocation(file));
                    context.report(incident);
                }
            }
        }
    }

    private static String getDensity(String folderName) {
        if (folderName.contains("-xxxhdpi")) return "xxxhdpi";
        if (folderName.contains("-xxhdpi")) return "xxhdpi";
        if (folderName.contains("-xhdpi")) return "xhdpi";
        if (folderName.contains("-hdpi")) return "hdpi";
        if (folderName.contains("-mdpi")) return "mdpi";
        if (folderName.contains("-ldpi")) return "ldpi";
        return null;
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
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
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