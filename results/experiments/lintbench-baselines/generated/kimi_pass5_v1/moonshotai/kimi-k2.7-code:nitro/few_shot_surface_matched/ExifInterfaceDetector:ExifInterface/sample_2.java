package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String FRAMEWORK_EXIF = "android.media.ExifInterface";
    private static final String SUPPORT_EXIF = "android.support.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using framework ExifInterface",
                    "The `android.media.ExifInterface` implementation has known security "
                            + "bugs in older versions of Android. You should use the support "
                            + "library version `"
                            + SUPPORT_EXIF
                            + "` instead.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

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
                if (node.isConstructor()) {
                    UReferenceExpression classRef = node.getClassReference();
                    if (classRef != null) {
                        checkResolved(classRef.resolve(), context, node);
                    }
                } else {
                    PsiMethod method = node.resolve();
                    if (method != null) {
                        checkResolved(method, context, node);
                    }
                    PsiType receiverType = node.getReceiverType();
                    if (receiverType instanceof PsiClassType) {
                        PsiClass cls = ((PsiClassType) receiverType).resolve();
                        checkClass(cls, context, node);
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(
                    UQualifiedReferenceExpression node) {
                checkResolved(node.resolve(), context, node);
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                UReferenceExpression importRef = node.getImportReference();
                if (importRef != null) {
                    checkResolved(importRef.resolve(), context, node);
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                PsiType type = node.getType();
                if (type instanceof PsiClassType) {
                    PsiClass cls = ((PsiClassType) type).resolve();
                    checkClass(cls, context, node);
                }
            }
        };
    }

    private static void checkResolved(PsiElement element, JavaContext context, UElement node) {
        if (element == null) {
            return;
        }
        if (element instanceof PsiClass) {
            checkClass((PsiClass) element, context, node);
        } else if (element instanceof PsiMember) {
            checkClass(((PsiMember) element).getContainingClass(), context, node);
        }
    }

    private static void checkClass(PsiClass cls, JavaContext context, UElement node) {
        if (cls != null && FRAMEWORK_EXIF.equals(cls.getQualifiedName())) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `android.media.ExifInterface` is discouraged; use `"
                            + SUPPORT_EXIF
                            + "` instead.");
        }
    }
}