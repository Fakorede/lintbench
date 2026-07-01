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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). "
                            + "This lint check identifies icons which do not have complete "
                            + "coverage across the densities. Low density is not really used "
                            + "much anymore, so this check ignores the ldpi density. To force "
                            + "lint to include it, set the environment variable "
                            + "`ANDROID_LINT_INCLUDE_LDPI=true`.",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Set<String>> iconDensities = new HashMap<>();
    private final Map<String, File> iconFiles = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);

        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File res : resourceFolders) {
            File[] drawables = res.listFiles();
            if (drawables == null) continue;
            for (File folder : drawables) {
                String folderName = folder.getName();
                if (folderName.startsWith("drawable")) {
                    String density = getDensity(folderName);
                    if (density == null) continue;

                    File[] files = folder.listFiles();
                    if (files == null) continue;
                    for (File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")) {
                            String iconName = name.substring(0, name.lastIndexOf('.'));
                            iconDensities.computeIfAbsent(iconName, k -> new HashSet<>()).add(density);
                            if (!iconFiles.containsKey(iconName)) {
                                iconFiles.put(iconName, file);
                            }
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        List<String> expectedDensities = new ArrayList<>();
        if (includeLdpi) {
            expectedDensities.add("ldpi");
        }
        expectedDensities.add("mdpi");
        expectedDensities.add("hdpi");
        expectedDensities.add("xhdpi");
        expectedDensities.add("xxhdpi");
        expectedDensities.add("xxxhdpi");

        for (Map.Entry<String, Set<String>> entry : iconDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> densities = entry.getValue();

            if (densities.size() == 1 && densities.contains("nodpi")) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String expected : expectedDensities) {
                if (!densities.contains(expected)) {
                    missing.add(expected);
                }
            }

            if (!missing.isEmpty() && densities.size() < expectedDensities.size()) {
                File file = iconFiles.get(iconName);
                if (file != null) {
                    Location location = Location.create(file);
                    String message = String.format(
                            "Icon '%s' is missing the following densities: %s",
                            iconName, String.join(", ", missing));
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void filterIncident(@NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(node);
            }
        };
    }

    public void visitMethod(@NonNull UMethod node) {
    }

    public void visitCallExpression(@NonNull UCallExpression node) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    public void visitClass(@NonNull UClass node) {
    }

    public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                UClass.class,
                USimpleNameReferenceExpression.class);
    }

    private String getDensity(String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (part.equals("ldpi") || part.equals("mdpi") || part.equals("hdpi")
                    || part.equals("xhdpi") || part.equals("xxhdpi") || part.equals("xxxhdpi")
                    || part.equals("nodpi")) {
                return part;
            }
        }
        return null;
    }
}