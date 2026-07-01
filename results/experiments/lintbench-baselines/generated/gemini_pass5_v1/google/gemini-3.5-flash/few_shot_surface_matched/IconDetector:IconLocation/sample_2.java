package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon really is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
                    )
            );

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        checkProject(context);
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public void filterIncident(@com.android.annotations.NonNull Incident incident) {
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@com.android.annotations.NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(@com.android.annotations.NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UMethod node) {
    }

    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
    }

    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                UClass.class,
                USimpleNameReferenceExpression.class
        );
    }

    private void checkProject(@com.android.annotations.NonNull Context context) {
        for (java.io.File resDir : context.getProject().getResourceFolders()) {
            java.io.File[] subdirs = resDir.listFiles();
            if (subdirs == null) continue;
            for (java.io.File subdir : subdirs) {
                String name = subdir.getName();
                if (name.equals("drawable") || (name.startsWith("drawable-") && !hasDensityQualifier(name))) {
                    java.io.File[] files = subdir.listFiles();
                    if (files == null) continue;
                    for (java.io.File file : files) {
                        if (isBitmapFile(file)) {
                            Location location = Location.create(file);
                            context.report(
                                    ISSUE,
                                    location,
                                    "Found bitmap file in density-independent folder `" + name + "`; "
                                            + "should be in a density-specific folder (e.g. `drawable-mdpi`) or `drawable-nodpi`"
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean hasDensityQualifier(String folderName) {
        return folderName.contains("-ldpi")
                || folderName.contains("-mdpi")
                || folderName.contains("-hdpi")
                || folderName.contains("-xhdpi")
                || folderName.contains("-xxhdpi")
                || folderName.contains("-xxxhdpi")
                || folderName.contains("-tvdpi")
                || folderName.contains("-nodpi")
                || folderName.contains("-anydpi");
    }

    private boolean isBitmapFile(java.io.File file) {
        String name = file.getName().toLowerCase(java.util.Locale.US);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }
}