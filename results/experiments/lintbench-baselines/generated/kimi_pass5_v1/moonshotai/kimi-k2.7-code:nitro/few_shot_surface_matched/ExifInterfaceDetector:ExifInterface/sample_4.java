package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiJavaCodeReferenceElement;
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

    private static final String ANDROID_MEDIA_EXIF = "android.media.ExifInterface";
    private static final String SUPPORT_EXIF = "android.support.media.ExifInterface";

    public static final Issue ISSUE =
            Issue.create(
                            "ExifInterface",
                            "Using platform ExifInterface",
                            "The `android.media.ExifInterface` implementation has known security "
                                    + "bugs on older versions of Android. Prefer the support library "
                                    + "implementation `android.support.media.ExifInterface` instead.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ExifInterfaceDetector() {}

    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UImportStatement.class,
                UVariable.class);
    }

    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (isAndroidMediaExif(containingClass)) {
                        report(
                                context,
                                node,
                                "Use `" + SUPPORT_EXIF + "` instead of `" + ANDROID_MEDIA_EXIF + "`");
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(
                    @NonNull UQualifiedReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    if (isAndroidMediaExif((PsiClass) resolved)) {
                        report(
                                context,
                                node,
                                "Use `" + SUPPORT_EXIF + "` instead of `" + ANDROID_MEDIA_EXIF + "`");
                    }
                } else if (resolved instanceof PsiField) {
                    PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                    if (isAndroidMediaExif(containingClass)) {
                        report(
                                context,
                                node,
                                "Use `" + SUPPORT_EXIF + "` instead of `" + ANDROID_MEDIA_EXIF + "`");
                    }
                }
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                PsiJavaCodeReferenceElement reference = node.getImportReference();
                if (reference != null) {
                    PsiElement resolved = reference.resolve();
                    if (resolved instanceof PsiClass && isAndroidMediaExif((PsiClass) resolved)) {
                        report(
                                context,
                                node,
                                "Use `" + SUPPORT_EXIF + "` instead of `" + ANDROID_MEDIA_EXIF + "`");
                    }
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                UTypeReferenceExpression typeReference = node.getTypeReference();
                if (typeReference != null) {
                    PsiElement resolved = typeReference.resolve();
                    if (resolved instanceof PsiClass && isAndroidMediaExif((PsiClass) resolved)) {
                        report(
                                context,
                                typeReference,
                                "Use `" + SUPPORT_EXIF + "` instead of `" + ANDROID_MEDIA_EXIF + "`");
                    }
                }
            }
        };
    }

    private static boolean isAndroidMediaExif(@Nullable PsiClass psiClass) {
        return psiClass != null && ANDROID_MEDIA_EXIF.equals(psiClass.getQualifiedName());
    }

    private static void report(
            @NonNull JavaContext context, @NonNull UElement node, @NonNull String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}