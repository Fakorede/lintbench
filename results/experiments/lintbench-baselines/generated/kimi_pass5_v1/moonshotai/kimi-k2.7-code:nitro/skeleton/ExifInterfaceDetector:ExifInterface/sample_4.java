package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.*;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The framework `android.media.ExifInterface` implementation has known security "
                            + "bugs in older versions of Android. Use the support library "
                            + "version (`androidx.exifinterface.media.ExifInterface`) instead.",
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
                UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                if (isExifInterface(node.resolve())) {
                    report(context, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                if (node.getUCallExpression() != null) {
                    return;
                }
                UElement parent = node.getUastParent();
                if (parent instanceof UCallExpression) {
                    return;
                }
                if (isExifInterface(node.resolve())) {
                    report(context, node);
                }
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                UElement ref = node.getImportReference();
                if (ref instanceof UReferenceExpression
                        && isExifInterface(((UReferenceExpression) ref).resolve())) {
                    report(context, node);
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                UTypeReferenceExpression typeRef = node.getTypeReference();
                if (typeRef != null && isExifInterface(typeRef.resolve())) {
                    report(context, node);
                }
            }
        };
    }

    private static boolean isExifInterface(PsiElement element) {
        if (element == null) {
            return false;
        }
        if (element instanceof PsiClass) {
            return EXIF_INTERFACE.equals(((PsiClass) element).getQualifiedName());
        }
        if (element instanceof PsiMethod) {
            PsiClass containingClass = ((PsiMethod) element).getContainingClass();
            return containingClass != null
                    && EXIF_INTERFACE.equals(containingClass.getQualifiedName());
        }
        if (element instanceof PsiField) {
            PsiClass containingClass = ((PsiField) element).getContainingClass();
            return containingClass != null
                    && EXIF_INTERFACE.equals(containingClass.getQualifiedName());
        }
        return false;
    }

    private static void report(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `android.media.ExifInterface`");
    }
}