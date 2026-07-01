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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

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
        return Arrays.asList(UImportStatement.class, UReferenceExpression.class, UCallExpression.class, UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                checkElement(node, context);
            }

            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                checkElement(node, context);
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                checkElement(node, context);
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                checkElement(node, context);
            }
        };
    }

    private void checkElement(@NonNull UElement node, @NonNull JavaContext context) {
        if (node instanceof UImportStatement) {
            UImportStatement imp = (UImportStatement) node;
            if (TARGET_CLASS.equals(imp.getQualifiedName())) {
                reportUsage(context, node);
            }
        } else if (node instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) node).resolve();
            if (resolved instanceof PsiClass && TARGET_CLASS.equals(((PsiClass) resolved).getQualifiedName())) {
                reportUsage(context, node);
            }
        }
    }

    private void reportUsage(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(ISSUE, node, context.getLocation(node),
                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security vulnerabilities on older Android versions.");
    }
}