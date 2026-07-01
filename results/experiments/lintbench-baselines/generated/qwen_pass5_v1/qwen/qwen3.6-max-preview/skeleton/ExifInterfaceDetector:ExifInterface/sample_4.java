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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallKind;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;

import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. " +
                    "There is a new implementation available of this library in the support library, which is preferable.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String TARGET_CLASS = "android.media.ExifInterface";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UReferenceExpression.class,
                UVariable.class,
                UCallExpression.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                checkNode(context, node);
            }

            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                checkNode(context, node);
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                checkNode(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                checkNode(context, node);
            }
        };
    }

    private void checkNode(@NonNull JavaContext context, @NonNull UElement node) {
        String qualifiedName = null;

        if (node instanceof UImportStatement) {
            qualifiedName = ((UImportStatement) node).getQualifiedName();
        } else if (node instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) node).resolve();
            if (resolved instanceof PsiClass) {
                qualifiedName = ((PsiClass) resolved).getQualifiedName();
            }
        } else if (node instanceof UVariable) {
            PsiType type = ((UVariable) node).getType();
            if (type != null) {
                qualifiedName = type.getCanonicalText();
            }
        } else if (node instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) node;
            if (call.getKind() == UCallKind.CONSTRUCTOR_CALL) {
                PsiElement resolved = call.resolve();
                if (resolved instanceof PsiMethod) {
                    PsiClass containingClass = ((PsiMethod) resolved).getContainingClass();
                    if (containingClass != null) {
                        qualifiedName = containingClass.getQualifiedName();
                    }
                }
            }
        }

        if (TARGET_CLASS.equals(qualifiedName)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`");
        }
    }
}