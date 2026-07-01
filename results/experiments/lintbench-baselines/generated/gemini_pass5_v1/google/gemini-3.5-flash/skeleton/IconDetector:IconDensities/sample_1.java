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
                            + "major screen density classes (low, medium, high, extra high). This "
                            + "lint check identifies icons which do not have complete coverage "
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

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        java.util.Map<String, ResourceInfo> resources = new java.util.HashMap<>();

        for (java.io.File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            java.io.File[] subDirs = resDir.listFiles();
            if (subDirs == null) {
                continue;
            }
            for (java.io.File subDir : subDirs) {
                if (!subDir.isDirectory()) {
                    continue;
                }
                String dirName = subDir.getName();
                String type = null;
                if (dirName.startsWith("drawable")) {
                    type = "drawable";
                } else if (dirName.startsWith("mipmap")) {
                    type = "mipmap";
                }
                if (type == null) {
                    continue;
                }

                String density = getDensity(dirName);
                boolean isAnyDpi = "anydpi".equals(density);
                boolean isNoDpi = "nodpi".equals(density);
                boolean hasDensityQualifier = (density != null && !isAnyDpi && !isNoDpi);

                java.io.File[] files = subDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (java.io.File file : files) {
                    String fileName = file.getName();
                    if (file.isDirectory() || fileName.startsWith(".")) {
                        continue;
                    }
                    int lastDot = fileName.lastIndexOf('.');
                    if (lastDot <= 0) {
                        continue;
                    }
                    String resName = fileName.substring(0, lastDot);
                    String ext = fileName.substring(lastDot + 1).toLowerCase();
                    boolean isXml = "xml".equals(ext);

                    if (!isXml && !isImageExtension(ext)) {
                        continue;
                    }

                    String key = type + ":" + resName;
                    ResourceInfo info = resources.get(key);
                    if (info == null) {
                        info = new ResourceInfo();
                        info.type = type;
                        info.name = resName;
                        resources.put(key, info);
                    }

                    if (isAnyDpi) {
                        info.hasAnyDpi = true;
                    } else if (isNoDpi) {
                        info.hasNoDpi = true;
                    } else if (hasDensityQualifier) {
                        info.densities.add(density);
                        if (!info.densityFiles.containsKey(density)) {
                            info.densityFiles.put(density, file);
                        }
                    } else {
                        info.hasDefault = true;
                        if (isXml) {
                            info.hasDefaultXml = true;
                        }
                        if (!info.densityFiles.containsKey("default")) {
                            info.densityFiles.put("default", file);
                        }
                    }

                    if (!isXml) {
                        info.isXmlOnly = false;
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (ResourceInfo info : resources.values()) {
            if (info.isXmlOnly) {
                continue;
            }
            if (info.hasAnyDpi || info.hasNoDpi || info.hasDefaultXml) {
                continue;
            }

            java.util.List<String> required = new java.util.ArrayList<>();
            if (includeLdpi) {
                required.add("ldpi");
            }
            required.add("mdpi");
            required.add("hdpi");
            required.add("xhdpi");
            required.add("xxhdpi");
            if ("mipmap".equals(info.type)) {
                required.add("xxxhdpi");
            }

            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String req : required) {
                if (!info.densities.contains(req)) {
                    if ("mdpi".equals(req) && info.hasDefault) {
                        continue;
                    }
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                java.io.File reportFile = null;
                for (String d : required) {
                    if (info.densities.contains(d)) {
                        reportFile = info.densityFiles.get(d);
                        if (reportFile != null) {
                            break;
                        }
                    }
                }
                if (reportFile == null && info.hasDefault) {
                    reportFile = info.densityFiles.get("default");
                }

                if (reportFile != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < missing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(missing.get(i));
                    }
                    String missingStr = sb.toString();
                    String message = String.format(
                            "Icon '%s' is missing the following densities: %s",
                            info.type + "/" + info.name,
                            missingStr);

                    Incident incident = new Incident(ISSUE);
                    incident.setLocation(context.getLocation(reportFile));
                    incident.setMessage(message);
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
            public void visitMethod(@NonNull UMethod node) {}

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {}
        };
    }

    private static String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi")
                    || segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")
                    || segment.equals("anydpi") || segment.equals("nodpi")) {
                return segment;
            }
        }
        return null;
    }

    private static boolean isImageExtension(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext) || "webp".equals(ext);
    }

    private static class ResourceInfo {
        String type;
        String name;
        java.util.Set<String> densities = new java.util.HashSet<>();
        java.util.Map<String, java.io.File> densityFiles = new java.util.HashMap<>();
        boolean hasAnyDpi = false;
        boolean hasNoDpi = false;
        boolean hasDefault = false;
        boolean hasDefaultXml = false;
        boolean isXmlOnly = true;
    }
}