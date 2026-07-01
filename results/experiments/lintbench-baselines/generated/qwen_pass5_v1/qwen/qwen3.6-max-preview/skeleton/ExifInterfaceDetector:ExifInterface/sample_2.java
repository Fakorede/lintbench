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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String EXIF_INTERFACE_FQN = "android.media.ExifInterface";

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. There is a new implementation available of this library in the support library, which is preferable.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UReferenceExpression.class, UImportStatement.class, UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                checkElement(context, node);
            }

            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                checkElement(context, node);
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                String fqn = node.getImportFqn();
                if (EXIF_INTERFACE_FQN.equals(fqn)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`");
                }
            }
        };
    }

    private void checkElement(@NonNull JavaContext context, @NonNull UElement node) {
        PsiElement resolved = null;
        if (node instanceof UReferenceExpression) {
            resolved = ((UReferenceExpression) node).resolve();
        } else if (node instanceof UCallExpression) {
            resolved = ((UCallExpression) node).resolve();
        }

        if (resolved instanceof PsiMethod) {
            resolved = ((PsiMethod) resolved).getContainingClass();
        }

        if (resolved instanceof PsiClass) {
            String qualifiedName = ((PsiClass) resolved).getQualifiedName();
            if (EXIF_INTERFACE_FQN.equals(qualifiedName)) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`");
            }
        }
    }
}