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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final List<Density> DENSITY_ORDER =
            Arrays.asList(Density.LDPI, Density.MDPI, Density.HDPI, Density.XHDPI);

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the major "
                            + "screen density classes (low, medium, high, extra high). This lint "
                            + "check identifies icons which do not have complete coverage across "
                            + "the densities.\n\nLow density is not really used much anymore, so "
                            + "this check ignores the ldpi density. To force lint to include it, "
                            + "set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For "
                            + "more information on current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, EnumSet<Density>> mDensities = new HashMap<>();
    private final Map<String, List<File>> mLocations = new HashMap<>();
    private final Set<String> mNames = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDensities.clear();
        mLocations.clear();
        mNames.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        File file = context.getFile();
        String fileName = file.getName();
        String baseName = getBaseName(fileName);
        if (baseName == null) {
            return;
        }

        Density density = getDensity(file);
        if (density == null
                || density == Density.NODPI
                || density == Density.ANYDPI) {
            return;
        }

        mDensities.computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class)).add(density);
        mLocations.computeIfAbsent(baseName, k -> new ArrayList<>()).add(file);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        EnumSet<Density> required = getRequiredDensities();

        for (String name : mNames) {
            EnumSet<Density> densities = mDensities.get(name);
            if (densities == null || densities.isEmpty()) {
                continue;
            }

            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(densities);
            if (missing.isEmpty()) {
                continue;
            }

            List<File> files = mLocations.get(name);
            if (files == null || files.isEmpty()) {
                continue;
            }

            File file = files.get(0);
            String message =
                    String.format(
                            "Missing the following density variations of icon `%1$s`: %2$s",
                            name,
                            formatDensities(missing));

            LintMap map = LintMap.obtain();
            map.putString("name", name);
            map.putString("densities", joinDensities(densities));

            Incident incident = new Incident(ISSUE, Location.create(file), message, map);
            context.report(incident);
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        String present = map.getString("densities", null);
        if (present == null) {
            return true;
        }

        EnumSet<Density> densities = EnumSet.noneOf(Density.class);
        for (String part : present.split(",")) {
            Density density = parseDensity(part);
            if (density != null) {
                densities.add(density);
            }
        }

        EnumSet<Density> required = getRequiredDensities();
        EnumSet<Density> missing = EnumSet.copyOf(required);
        missing.removeAll(densities);
        if (missing.isEmpty()) {
            return false;
        }

        String name = map.getString("name", null);
        if (name != null) {
            incident.setMessage(
                    String.format(
                            "Missing the following density variations of icon `%1$s`: %2$s",
                            name,
                            formatDensities(missing)));
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        addIconName(element.getAttributeNS(ANDROID_URI, "icon"));
        addIconName(element.getAttributeNS(ANDROID_URI, "logo"));
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not needed for density validation.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not needed for density validation.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not needed for density validation; R.drawable references are handled
                // by visitSimpleNameReferenceExpression.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiField) {
                    PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                    if (containingClass != null) {
                        String className = containingClass.getName();
                        if ("drawable".equals(className) || "mipmap".equals(className)) {
                            mNames.add(node.getIdentifier());
                        }
                    }
                }
            }
        };
    }

    private void addIconName(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
            int start = value.indexOf('/') + 1;
            int end = value.indexOf('?', start);
            if (end < 0) {
                end = value.length();
            }
            mNames.add(value.substring(start, end));
        }
    }

    private static EnumSet<Density> getRequiredDensities() {
        EnumSet<Density> required = EnumSet.of(Density.MDPI, Density.HDPI, Density.XHDPI);
        if (includeLdpi()) {
            required.add(Density.LDPI);
        }
        return required;
    }

    private static boolean includeLdpi() {
        String value = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        return value != null && Boolean.parseBoolean(value);
    }

    private static Density getDensity(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }
        String folder = parent.getName();
        int dash = folder.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String[] parts = folder.split("-");
        for (int i = 1; i < parts.length; i++) {
            Density density = parseDensity(parts[i]);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    private static Density parseDensity(String qualifier) {
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
            case "anydpi":
                return Density.ANYDPI;
            default:
                return null;
        }
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (extension.equals("png")
                || extension.equals("jpg")
                || extension.equals("jpeg")
                || extension.equals("gif")
                || extension.equals("webp")) {
            return fileName.substring(0, dot);
        }
        return null;
    }

    private static String formatDensities(EnumSet<Density> densities) {
        StringBuilder sb = new StringBuilder();
        for (Density density : DENSITY_ORDER) {
            if (densities.contains(density)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(density.name().toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static String joinDensities(EnumSet<Density> densities) {
        StringBuilder sb = new StringBuilder();
        for (Density density : densities) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(density.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}