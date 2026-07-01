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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        if (element.getParentNode() != null) {
            return;
        }
        java.io.File file = context.file;
        java.io.File parentDir = file.getParentFile();
        if (parentDir != null && "drawable".equals(parentDir.getName())) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Image defined in density-independent drawable folder");
            }
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(null, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(null, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(null, node);
            }
        };
    }

    @Override
    public void visitMethod(@Nullable JavaContext context, @Nullable UCallExpression node, @NonNull PsiMethod method) {
        // No-op for this issue
    }

    @Override
    public void visitCallExpression(@Nullable JavaContext context, @NonNull UCallExpression node) {
        // No-op for this issue
    }

    @Override
    public void visitClass(@Nullable JavaContext context, @NonNull UClass declaration) {
        // No-op for this issue
    }

    @Override
    public void visitSimpleNameReferenceExpression(@Nullable JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op for this issue
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.emptyList();
    }
}