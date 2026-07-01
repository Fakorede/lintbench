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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_MEDIA_EXIFINTERFACE = "android.media.ExifInterface";

    public static final Issue EXIF_INTERFACE =
            Issue.create(
                            "ExifInterface",
                            "Avoid android.media.ExifInterface",
                            "The `android.media.ExifInterface` implementation has known security "
                                    + "bugs on older versions of Android. Prefer the ExifInterface "
                                    + "support library instead.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ExifInterfaceDetector() {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.<Class<? extends UElement>>asList(
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
                PsiMethod method = node.resolve();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    checkClass(containingClass, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                checkElement(node.resolve(), node);
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                checkElement(node.resolve(), node);
            }

            @Override
            public void visitVariable(UVariable node) {
                UTypeReferenceExpression typeReference = node.getTypeReference();
                if (typeReference != null) {
                    PsiClass psiClass = typeReference.resolve();
                    checkClass(psiClass, node);
                }
            }

            private void checkElement(PsiElement resolved, UElement node) {
                if (resolved instanceof PsiMember) {
                    checkClass(((PsiMember) resolved).getContainingClass(), node);
                } else if (resolved instanceof PsiClass) {
                    checkClass((PsiClass) resolved, node);
                }
            }

            private void checkClass(PsiClass psiClass, UElement node) {
                if (psiClass != null
                        && ANDROID_MEDIA_EXIFINTERFACE.equals(psiClass.getQualifiedName())) {
                    context.report(
                            EXIF_INTERFACE,
                            node,
                            context.getNameLocation(node),
                            "Avoid using `android.media.ExifInterface`; use the support library version instead");
                }
            }
        };
    }
}