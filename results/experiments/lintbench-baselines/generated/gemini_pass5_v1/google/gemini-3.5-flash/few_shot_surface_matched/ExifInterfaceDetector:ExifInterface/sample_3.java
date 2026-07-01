package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
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
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    public ExifInterfaceDetector() {}

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
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                        context.report(
                                ISSUE,
                                node,
                                context.getCallLocation(node, true, false),
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                        );
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    if ("android.media.ExifInterface".equals(((PsiClass) resolved).getQualifiedName())) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                        );
                    }
                } else if (resolved instanceof PsiField) {
                    PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                    if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
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
            public void visitImportStatement(UImportStatement node) {
                String importString = node.getImportString();
                if (importString != null && importString.startsWith("android.media.ExifInterface")) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                    );
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                PsiType type = node.getType();
                if (type instanceof PsiClassType) {
                    PsiClass psiClass = ((PsiClassType) type).resolve();
                    if (psiClass != null && "android.media.ExifInterface".equals(psiClass.getQualifiedName())) {
                        UElement typeReference = node.getTypeReference();
                        Location location = typeReference != null ? context.getLocation(typeReference) : context.getLocation(node);
                        context.report(
                                ISSUE,
                                node,
                                location,
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`"
                        );
                    }
                }
            }
        };
    }
}