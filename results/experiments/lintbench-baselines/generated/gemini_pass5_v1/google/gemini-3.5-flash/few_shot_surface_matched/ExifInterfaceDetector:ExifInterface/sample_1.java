package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "ExifInterface",
                            "Using android.media.ExifInterface",
                            "The `android.media.ExifInterface` implementation has some known "
                                    + "security bugs in older versions of Android. There is a new "
                                    + "implementation available of this library in the support "
                                    + "library, which is preferable.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ExifInterfaceDetector() {}

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UVariable.class,
                UCallExpression.class,
                UQualifiedReferenceExpression.class
        );
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                ExifInterfaceDetector.this.visitImportStatement(context, node);
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                ExifInterfaceDetector.this.visitVariable(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                ExifInterfaceDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                ExifInterfaceDetector.this.visitQualifiedReferenceExpression(context, node);
            }
        };
    }

    public void visitImportStatement(@NonNull JavaContext context, @NonNull UImportStatement node) {
        if (node.getImportReference() != null) {
            String fqName = node.getImportReference().asSourceString().replaceAll("\\s", "");
            if ("android.media.ExifInterface".equals(fqName)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead"
                );
            }
        }
    }

    public void visitVariable(@NonNull JavaContext context, @NonNull UVariable node) {
        PsiType type = node.getType();
        if (type != null && "android.media.ExifInterface".equals(type.getCanonicalText())) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `androidx.exifinterface.media.ExifInterface` instead"
            );
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        PsiMethod method = node.resolve();
        if (method != null) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead"
                );
            }
        }
    }

    public void visitQualifiedReferenceExpression(@NonNull JavaContext context, @NonNull UQualifiedReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiField) {
            PsiClass containingClass = ((PsiField) resolved).getContainingClass();
            if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead"
                );
            }
        }
    }
}