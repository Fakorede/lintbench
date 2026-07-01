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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
                            + "set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For "
                            + "more information on current density usage, see "
                            + "https://developer.android.com/about/dashboards",
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
        if (context.getProject() != context.getMainProject()) {
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        Set<String> xmlResources = new HashSet<>();
        Map<String, Map<String, File>> iconDensities = new HashMap<>();

        for (File resFolder : resourceFolders) {
            File[] folders = resFolder.listFiles();
            if (folders == null) continue;
            for (File folder : folders) {
                String folderName = folder.getName();
                if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                    boolean isMipmap = folderName.startsWith("mipmap");
                    String density = getDensity(folderName);

                    if (density == null) {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.getName().endsWith(".xml")) {
                                    String baseName = getBaseName(file.getName());
                                    xmlResources.add((isMipmap ? "mipmap:" : "drawable:") + baseName);
                                }
                            }
                        }
                    } else {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                String fileName = file.getName();
                                if (isBitmap(fileName)) {
                                    String baseName = getBaseName(fileName);
                                    String key = (isMipmap ? "mipmap:" : "drawable:") + baseName;
                                    iconDensities.computeIfAbsent(key, k -> new HashMap<>()).put(density, file);
                                }
                            }
                        }
                    }
                }
            }
        }

        Set<String> expected = new LinkedHashSet<>();
        if ("true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            expected.add("ldpi");
        }
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        for (Map.Entry<String, Map<String, File>> entry : iconDensities.entrySet()) {
            String key = entry.getKey();
            if (xmlResources.contains(key)) {
                continue;
            }

            Map<String, File> present = entry.getValue();
            List<String> missing = new ArrayList<>();
            for (String d : expected) {
                if (!present.containsKey(d)) {
                    missing.add(d);
                }
            }

            if (!missing.isEmpty() && present.size() < expected.size()) {
                File existingFile = present.values().iterator().next();
                String resourceName = key.substring(key.indexOf(':') + 1);

                StringBuilder sb = new StringBuilder();
                sb.append("Icon '").append(resourceName).append("' is missing densities: ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(missing.get(i));
                }

                Incident incident = new Incident(ISSUE, Location.create(existingFile), sb.toString());
                context.report(incident);
            }
        }
    }

    private static String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")) {
                return segment;
            }
        }
        return null;
    }

    private static boolean isBitmap(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private static String getBaseName(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            return fileName.substring(0, index);
        }
        return fileName;
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