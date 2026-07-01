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
                UImportStatement.class,
                UCallExpression.class,
                UVariable.class,
                UQualifiedReferenceExpression.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                UElement importVal = node.getImportExpression();
                if (importVal != null) {
                    String importName = importVal.asSourceString();
                    if ("android.media.ExifInterface".equals(importName)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security bugs on older platforms"
                        );
                    }
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                PsiMethod resolved = node.resolve();
                if (resolved != null) {
                    PsiClass containingClass = resolved.getContainingClass();
                    if (containingClass != null && "android.media.ExifInterface".equals(containingClass.getQualifiedName())) {
                        context.report(
                                ISSUE,
                                node,
                                context.getCallLocation(node, true, true),
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security bugs on older platforms"
                        );
                    }
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                PsiType type = node.getType();
                if (type != null && "android.media.ExifInterface".equals(type.getCanonicalText())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security bugs on older platforms"
                    );
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) resolved;
                    if ("android.media.ExifInterface".equals(psiClass.getQualifiedName())) {
                        UElement parent = node.getUastParent();
                        while (parent != null) {
                            if (parent instanceof UImportStatement || parent instanceof UVariable || parent instanceof UCallExpression) {
                                return;
                            }
                            parent = parent.getUastParent();
                        }
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security bugs on older platforms"
                        );
                    }
                }
            }
        };
    }
}