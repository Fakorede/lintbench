package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIFINTERFACE = "android.media.ExifInterface";
    private static final String SUPPORT_EXIFINTERFACE = "android.support.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has known security bugs in older versions of Android. "
                            + "You should use the support library implementation (`"
                            + SUPPORT_EXIFINTERFACE
                            + "`) instead.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UCallExpression.class,
                UImportStatement.class,
                UReferenceExpression.class,
                UQualifiedReferenceExpression.class,
                UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiMethod) {
                    PsiClass containingClass = ((PsiMethod) resolved).getContainingClass();
                    if (containingClass != null
                            && ANDROID_MEDIA_EXIFINTERFACE.equals(containingClass.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) resolved;
                    if (ANDROID_MEDIA_EXIFINTERFACE.equals(psiClass.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            @Override
            public void visitReferenceExpression(UReferenceExpression node) {
                checkReference(context, node);
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                checkReference(context, node);
            }

            @Override
            public void visitVariable(UVariable node) {
                PsiType type = node.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null
                            && ANDROID_MEDIA_EXIFINTERFACE.equals(resolved.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            private void checkReference(JavaContext context, UReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) resolved;
                    if (ANDROID_MEDIA_EXIFINTERFACE.equals(psiClass.getQualifiedName())) {
                        report(context, node);
                    }
                } else if (resolved instanceof PsiMember) {
                    PsiClass containingClass = ((PsiMember) resolved).getContainingClass();
                    if (containingClass != null
                            && ANDROID_MEDIA_EXIFINTERFACE.equals(containingClass.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            private void report(JavaContext context, UElement node) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `android.media.ExifInterface`");
            }
        };
    }
}