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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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
                            + "set the environment variable ANDROID_LINT_INCLUDE_LDPI=true.",
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
        java.util.List<String> densitiesList = java.util.Arrays.asList("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        java.util.Set<String> xmlResources = new java.util.HashSet<>();
        java.util.Map<ResourceKey, ResourceInfo> resources = new java.util.HashMap<>();

        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] subdirs = resFolder.listFiles();
            if (subdirs == null) continue;
            for (java.io.File subdir : subdirs) {
                if (!subdir.isDirectory()) continue;
                String dirName = subdir.getName();
                if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) {
                    continue;
                }

                java.io.File[] files = subdir.listFiles();
                if (files == null) continue;

                FolderInfo folderInfo = parseFolder(dirName, densitiesList);

                for (java.io.File file : files) {
                    if (file.isDirectory()) continue;
                    String fileName = file.getName();
                    if (fileName.endsWith(".xml")) {
                        xmlResources.add(getResourceName(fileName));
                    } else if (isBitmap(fileName)) {
                        if (folderInfo != null) {
                            String resName = getResourceName(fileName);
                            ResourceKey key = new ResourceKey(resName, folderInfo.configKey);
                            ResourceInfo info = resources.get(key);
                            if (info == null) {
                                info = new ResourceInfo(resName, folderInfo.configKey, folderInfo.isMipmap);
                                resources.put(key, info);
                            }
                            info.densityFiles.put(folderInfo.density, file);
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (java.util.Map.Entry<ResourceKey, ResourceInfo> entry : resources.entrySet()) {
            ResourceKey key = entry.getKey();
            ResourceInfo info = entry.getValue();

            if (xmlResources.contains(key.name)) {
                continue;
            }

            java.util.List<String> expected = new java.util.ArrayList<>();
            if (includeLdpi) {
                expected.add("ldpi");
            }
            expected.add("mdpi");
            expected.add("hdpi");
            expected.add("xhdpi");
            expected.add("xxhdpi");
            if (info.isMipmap) {
                expected.add("xxxhdpi");
            }

            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String d : expected) {
                if (!info.densityFiles.containsKey(d)) {
                    missing.add(d);
                }
            }

            if (!missing.isEmpty()) {
                java.io.File reportFile = null;
                for (java.io.File f : info.densityFiles.values()) {
                    reportFile = f;
                    break;
                }

                if (reportFile != null) {
                    String message = String.format(
                            "Icon '%s' is missing the following densities in %s: %s",
                            key.name,
                            key.configKey,
                            String.join(", ", missing)
                    );

                    Incident incident = new Incident(ISSUE, message, context.getLocation(reportFile));
                    context.report(incident);
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

    private static class FolderInfo {
        final String configKey;
        final String density;
        final boolean isMipmap;

        FolderInfo(String configKey, String density, boolean isMipmap) {
            this.configKey = configKey;
            this.density = density;
            this.isMipmap = isMipmap;
        }
    }

    private FolderInfo parseFolder(String name, java.util.List<String> densitiesList) {
        boolean isMipmap = name.startsWith("mipmap");
        String[] parts = name.split("-");
        String density = null;
        java.util.List<String> remaining = new java.util.ArrayList<>();
        for (String part : parts) {
            if (densitiesList.contains(part)) {
                density = part;
            } else {
                remaining.add(part);
            }
        }
        if (density == null) {
            return null;
        }
        String configKey = String.join("-", remaining);
        return new FolderInfo(configKey, density, isMipmap);
    }

    private boolean isBitmap(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private String getResourceName(String filename) {
        int dot = filename.indexOf('.');
        if (dot != -1) {
            return filename.substring(0, dot);
        }
        return filename;
    }

    private static class ResourceKey {
        final String name;
        final String configKey;

        ResourceKey(String name, String configKey) {
            this.name = name;
            this.configKey = configKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ResourceKey)) return false;
            ResourceKey that = (ResourceKey) o;
            return name.equals(that.name) && configKey.equals(that.configKey);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + configKey.hashCode();
        }
    }

    private static class ResourceInfo {
        final String name;
        final String configKey;
        final boolean isMipmap;
        final java.util.Map<String, java.io.File> densityFiles = new java.util.HashMap<>();

        ResourceInfo(String name, String configKey, boolean isMipmap) {
            this.name = name;
            this.configKey = configKey;
            this.isMipmap = isMipmap;
        }
    }
}