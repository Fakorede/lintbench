package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known "
                            + "security bugs in older versions of Android. There is a new "
                            + "implementation available of this library in the support "
                            + "library, which is preferable.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UImportStatement.class,
                UVariable.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            private boolean hasReported = false;

            private void report(UElement node) {
                if (!hasReported) {
                    hasReported = true;
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                    );
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                PsiMethod resolved = node.resolve();
                if (resolved != null) {
                    PsiClass containingClass = resolved.getContainingClass();
                    if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                        report(node);
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                UExpression selector = node.getSelector();
                if (selector instanceof org.jetbrains.uast.UResolvable) {
                    PsiElement resolved = ((org.jetbrains.uast.UResolvable) selector).resolve();
                    if (resolved instanceof PsiClass) {
                        if ("android.media.ExifInterface".equals(((PsiClass) resolved).getQualifiedName())) {
                            report(node);
                            return;
                        }
                    } else if (resolved instanceof PsiField) {
                        PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                        if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                            report(node);
                            return;
                        }
                    }
                }
                if (node.asSourceString().contains("android.media.ExifInterface")) {
                    report(node);
                }
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                String importString = node.getImportString();
                if (importString != null && importString.contains("android.media.ExifInterface")) {
                    report(node);
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                PsiType type = node.getType();
                if (type != null && "android.media.ExifInterface".equals(type.getCanonicalText())) {
                    report(node);
                }
            }
        };
    }
}