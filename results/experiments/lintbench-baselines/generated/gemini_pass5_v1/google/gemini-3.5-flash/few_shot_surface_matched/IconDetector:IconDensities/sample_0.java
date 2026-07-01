package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.*;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES
                    )
            );

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization if needed
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        java.util.Map<String, java.util.Set<String>> iconToDensities = new java.util.HashMap<>();
        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] dirs = resFolder.listFiles();
            if (dirs == null) continue;

            for (java.io.File dir : dirs) {
                String dirName = dir.getName();
                if (dirName.startsWith("drawable-")) {
                    String density = getDensity(dirName);
                    if (density == null) continue;
                    if (density.equals("ldpi") && !includeLdpi) {
                        continue;
                    }

                    java.io.File[] files = dir.listFiles();
                    if (files == null) continue;

                    for (java.io.File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp") || name.endsWith(".gif")) {
                            if (name.endsWith(".9.png")) {
                                continue;
                            }
                            java.util.Set<String> densities = iconToDensities.get(name);
                            if (densities == null) {
                                densities = new java.util.HashSet<>();
                                iconToDensities.put(name, densities);
                            }
                            densities.add(density);
                        }
                    }
                }
            }
        }

        java.util.List<String> expectedDensities = new java.util.ArrayList<>();
        if (includeLdpi) {
            expectedDensities.add("ldpi");
        }
        expectedDensities.add("mdpi");
        expectedDensities.add("hdpi");
        expectedDensities.add("xhdpi");
        expectedDensities.add("xxhdpi");
        expectedDensities.add("xxxhdpi");

        for (java.util.Map.Entry<String, java.util.Set<String>> entry : iconToDensities.entrySet()) {
            String iconName = entry.getKey();
            java.util.Set<String> presentDensities = entry.getValue();

            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String density : expectedDensities) {
                if (!presentDensities.contains(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty() && presentDensities.size() < expectedDensities.size()) {
                String message = String.format("Icon `%s` is missing densities: %s", iconName, missing.toString());
                Incident incident = new Incident(ISSUE, message, Location.create(context.getProject().getDir()));
                context.report(incident);
            }
        }
    }

    private String getDensity(String dirName) {
        String[] parts = dirName.split("-");
        for (String part : parts) {
            if (part.equals("ldpi") || part.equals("mdpi") || part.equals("hdpi") ||
                part.equals("xhdpi") || part.equals("xxhdpi") || part.equals("xxxhdpi")) {
                return part;
            }
        }
        return null;
    }

    @Override
    public void filterIncident(@NonNull Context context, @NonNull Incident incident) {
        // Optional custom incident filtering
    }

    @Override
    public boolean appliesTo(@NonNull Project project) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        // XML resource analysis
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Java/Kotlin code analysis for methods
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Java/Kotlin code analysis for call expressions
    }

    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Java/Kotlin code analysis for classes
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Java/Kotlin code analysis for simple name reference expressions
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UMethod.class);
        types.add(UClass.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }
}