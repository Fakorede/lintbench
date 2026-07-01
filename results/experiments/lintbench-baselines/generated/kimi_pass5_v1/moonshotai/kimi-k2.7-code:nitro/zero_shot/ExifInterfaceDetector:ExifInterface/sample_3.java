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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",
            "The `android.media.ExifInterface` implementation has some known security bugs in "
                    + "older versions of Android. There is a new implementation available of this "
                    + "library in the support library, which is preferable.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    ExifInterfaceDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, UReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NotNull UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null && method.getContainingClass() != null
                        && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                        method.getContainingClass().getQualifiedName())) {
                    report(context, node);
                }
            }

            @Override
            public void visitReferenceExpression(@NotNull UReferenceExpression node) {
                UElement parent = node.getUastParent();
                if (parent instanceof UCallExpression) {
                    // The call itself is reported above.
                    return;
                }

                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass
                        && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                        ((PsiClass) resolved).getQualifiedName())) {
                    report(context, node);
                } else if (resolved instanceof PsiField) {
                    PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                    if (containingClass != null
                            && ANDROID_MEDIA_EXIF_INTERFACE.equals(
                            containingClass.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }
        };
    }

    private void report(@NotNull JavaContext context, @NotNull UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `android.media.ExifInterface` is not recommended; "
                        + "use the support library ExifInterface instead");
    }
}