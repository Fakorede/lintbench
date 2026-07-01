package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UElementHandler;

import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String ANDROID_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String SUPPORT_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "Using `android.media.ExifInterface`\n\n"
                    + "The `android.media.ExifInterface` implementation has some known "
                    + "security bugs in older versions of Android. There is a new "
                    + "implementation available of this library in the support "
                    + "library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                USimpleNameReferenceExpression.class,
                UQualifiedReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression node) {
                check(context, node);
            }

            @Override
            public void visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
                check(context, node);
            }
        };
    }

    private static void check(JavaContext context, UReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) resolved;
            if (ANDROID_EXIF_INTERFACE.equals(psiClass.getQualifiedName())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `" + SUPPORT_EXIF_INTERFACE + "` instead of `android.media.ExifInterface`");
            }
        }
    }
}