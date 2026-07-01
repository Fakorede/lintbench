package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UResolvable;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";

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
                    IMPLEMENTATION);

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UImportStatement.class,
                UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                check(context, node);
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                check(context, node);
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                check(context, node);
            }

            @Override
            public void visitVariable(UVariable node) {
                check(context, node);
            }
        };
    }

    private void check(JavaContext context, UElement node) {
        String qualifiedName = null;

        if (node instanceof UImportStatement) {
            qualifiedName = ((UImportStatement) node).getQualifiedName();
        } else if (node instanceof UResolvable) {
            PsiElement resolved = ((UResolvable) node).resolve();
            if (resolved instanceof PsiClass) {
                qualifiedName = ((PsiClass) resolved).getQualifiedName();
            } else if (resolved instanceof PsiMethod) {
                PsiClass containingClass = ((PsiMethod) resolved).getContainingClass();
                if (containingClass != null) {
                    qualifiedName = containingClass.getQualifiedName();
                }
            } else if (resolved instanceof PsiField) {
                PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                if (containingClass != null) {
                    qualifiedName = containingClass.getQualifiedName();
                }
            }
        }

        if (qualifiedName == null && node instanceof UVariable) {
            PsiType type = ((UVariable) node).getType();
            if (type instanceof PsiClassType) {
                PsiClass psiClass = ((PsiClassType) type).resolve();
                if (psiClass != null) {
                    qualifiedName = psiClass.getQualifiedName();
                }
            }
        }

        if (OLD_EXIF_INTERFACE.equals(qualifiedName)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `androidx.exifinterface.media.ExifInterface` instead of "
                            + "`android.media.ExifInterface` to avoid security vulnerabilities "
                            + "on older Android versions.");
        }
    }
}