package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.UastScanner {
    private static final String OLD_EXIF = "android.media.ExifInterface";
    private static final String NEW_EXIF = "androidx.exifinterface.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
        "ExifInterface",
        "Using `android.media.ExifInterface`",
        "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. " +
        "There is a new implementation available of this library in the support library, which is preferable.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UImportStatement.class, UReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NotNull UImportStatement node) {
                String qualifiedName = node.getQualifiedName();
                if (OLD_EXIF.equals(qualifiedName)) {
                    reportUsage(context, node);
                }
            }

            @Override
            public void visitReferenceExpression(@NotNull UReferenceExpression node) {
                if (node.getUastParent() instanceof UImportStatement) {
                    return;
                }
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    String fqn = ((PsiClass) resolved).getQualifiedName();
                    if (OLD_EXIF.equals(fqn)) {
                        reportUsage(context, node);
                    }
                }
            }
        };
    }

    private void reportUsage(@NotNull JavaContext context, @NotNull UElement node) {
        LintFix fix = fix()
            .replace()
            .text(OLD_EXIF)
            .with(NEW_EXIF)
            .autoFix()
            .build();

        context.report(ISSUE, context.getLocation(node),
            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`", fix);
    }
}