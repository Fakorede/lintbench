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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";

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
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    public ExifInterfaceDetector() {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(UQualifiedReferenceExpression.class);
        types.add(UImportStatement.class);
        types.add(UVariable.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                ExifInterfaceDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                ExifInterfaceDetector.this.visitQualifiedReferenceExpression(context, node);
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                ExifInterfaceDetector.this.visitImportStatement(context, node);
            }

            @Override
            public void visitVariable(UVariable node) {
                ExifInterfaceDetector.this.visitVariable(context, node);
            }
        };
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        PsiMethod resolved = node.resolve();
        if (resolved != null) {
            PsiClass containingClass = resolved.getContainingClass();
            if (containingClass != null && OLD_EXIF_INTERFACE.equals(containingClass.getQualifiedName())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                );
            }
        }
    }

    @Override
    public void visitQualifiedReferenceExpression(JavaContext context, UQualifiedReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiClass) {
            if (OLD_EXIF_INTERFACE.equals(((PsiClass) resolved).getQualifiedName())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                );
            }
        }
    }

    @Override
    public void visitImportStatement(JavaContext context, UImportStatement node) {
        if (node.getImportReference() != null) {
            String importName = node.getImportReference().asSourceString();
            if (OLD_EXIF_INTERFACE.equals(importName)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                );
            }
        }
    }

    @Override
    public void visitVariable(JavaContext context, UVariable node) {
        PsiType type = node.getType();
        if (OLD_EXIF_INTERFACE.equals(type.getCanonicalText())) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
            );
        }
    }
}