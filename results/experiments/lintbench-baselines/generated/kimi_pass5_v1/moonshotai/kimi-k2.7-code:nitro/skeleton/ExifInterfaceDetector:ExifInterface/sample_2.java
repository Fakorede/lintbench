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
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastCallKind;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

    private static final String EXPLANATION =
            "The `android.media.ExifInterface` implementation has known security bugs in older "
                    + "versions of Android. Prefer the support library implementation instead.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>unmodifiableList(
                Arrays.asList(
                        UCallExpression.class,
                        UImportStatement.class,
                        UQualifiedReferenceExpression.class,
                        UVariable.class));
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                if (node.getKind() == UastCallKind.CONSTRUCTOR_ALLOCATION) {
                    PsiMethod method = node.resolve();
                    if (method != null) {
                        PsiClass containingClass = method.getContainingClass();
                        if (containingClass != null
                                && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                                        containingClass.getQualifiedName())) {
                            report(context, node);
                        }
                    }
                }
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                PsiImportStatement psiImportStatement = (PsiImportStatement) node.getPsi();
                if (psiImportStatement != null && !psiImportStatement.isOnDemand()) {
                    PsiJavaCodeReferenceElement importReference =
                            psiImportStatement.getImportReference();
                    if (importReference != null
                            && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                                    importReference.getCanonicalText())) {
                        report(context, node);
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(
                    UQualifiedReferenceExpression node) {
                UExpression receiver = node.getReceiver();
                if (receiver instanceof UReferenceExpression) {
                    PsiElement resolved = ((UReferenceExpression) receiver).resolve();
                    if (resolved instanceof PsiClass
                            && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                                    ((PsiClass) resolved).getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                PsiType type = node.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null
                            && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                                    resolved.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }
        };
    }

    private static void report(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `android.media.ExifInterface` is discouraged; use the support library version instead");
    }
}