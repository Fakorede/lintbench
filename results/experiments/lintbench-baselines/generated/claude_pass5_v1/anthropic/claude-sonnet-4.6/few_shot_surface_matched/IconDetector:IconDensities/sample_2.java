package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final boolean INCLUDE_LDPI;

    static {
        String includeLdpi = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        INCLUDE_LDPI = "true".equals(includeLdpi);
    }

    private static final String[] DENSITY_QUALIFIERS;

    static {
        List<String> densities = new ArrayList<>();
        if (INCLUDE_LDPI) {
            densities.add("ldpi");
        }
        densities.add("mdpi");
        densities.add("hdpi");
        densities.add("xhdpi");
        densities.add("xxhdpi");
        densities.add("xxxhdpi");
        DENSITY_QUALIFIERS = densities.toArray(new String[0]);
    }

    public static final Issue ICON_DENSITIES =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). "
                            + "This lint check identifies icons which do not have complete coverage "
                            + "across the densities.\n"
                            + "\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    // Map from icon name to set of densities it is present in
    private Map<String, Set<String>> iconDensityMap;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconDensityMap = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (iconDensityMap == null) {
            return;
        }

        Project project = context.getProject();

        // Scan res directories for drawable folders
        List<File> resDirs = project.getResourceFolders();
        for (File resDir : resDirs) {
            if (!resDir.exists()) {
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
                // Determine density qualifier
                String density = getDensityFromFolder(folderName);
                if (density == null) {
                    continue;
                }
                // List icons in this folder
                File[] icons = folder.listFiles();
                if (icons == null) {
                    continue;
                }
                for (File icon : icons) {
                    String iconName = icon.getName();
                    if (!isIconFile(iconName)) {
                        continue;
                    }
                    // Normalize name (strip extension)
                    String baseName = getBaseName(iconName);
                    Set<String> densities = iconDensityMap.get(baseName);
                    if (densities == null) {
                        densities = new HashSet<>();
                        iconDensityMap.put(baseName, densities);
                    }
                    densities.add(density);
                }
            }
        }

        // Now check for missing densities
        List<String> requiredDensities = Arrays.asList(DENSITY_QUALIFIERS);

        for (Map.Entry<String, Set<String>> entry : iconDensityMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentDensities = entry.getValue();

            List<String> missingDensities = new ArrayList<>();
            for (String required : requiredDensities) {
                if (!presentDensities.contains(required)) {
                    missingDensities.add(required);
                }
            }

            if (!missingDensities.isEmpty()) {
                // Find a location: use the first found file
                File locationFile = findIconFile(resDirs, iconName, presentDensities);
                Location location;
                if (locationFile != null) {
                    location = Location.create(locationFile);
                } else {
                    location = Location.create(project.getDir());
                }

                String message =
                        "The icon `"
                                + iconName
                                + "` does not have a drawable resource for the following "
                                + "densities: "
                                + formatList(missingDensities);

                Incident incident = new Incident(ICON_DENSITIES, location, message);
                context.report(incident);
            }
        }

        iconDensityMap = null;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op: we handle icons via file system scanning in afterCheckEachProject
    }

    @Nullable
    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(
            @NonNull JavaContext context) {
        return null;
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // No-op
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // No-op
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // No-op
    }

    // --- Helper methods ---

    @Nullable
    private static String getDensityFromFolder(@NonNull String folderName) {
        // e.g. "drawable-hdpi", "drawable-xhdpi", "drawable-mdpi"
        if (folderName.equals("drawable")) {
            // No density qualifier
            return null;
        }
        String[] parts = folderName.split("-");
        for (String part : parts) {
            for (String density : getAllDensities()) {
                if (part.equals(density)) {
                    return density;
                }
            }
        }
        return null;
    }

    private static String[] getAllDensities() {
        return new String[]{"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    }

    private static boolean isIconFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    @Nullable
    private static File findIconFile(
            @NonNull List<File> resDirs,
            @NonNull String baseName,
            @NonNull Set<String> densities) {
        for (File resDir : resDirs) {
            if (!resDir.exists()) {
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
                String density = getDensityFromFolder(folderName);
                if (density == null || !densities.contains(density)) {
                    continue;
                }
                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File f : files) {
                    if (getBaseName(f.getName()).equals(baseName) && isIconFile(f.getName())) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    private static String formatList(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                if (i == items.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}