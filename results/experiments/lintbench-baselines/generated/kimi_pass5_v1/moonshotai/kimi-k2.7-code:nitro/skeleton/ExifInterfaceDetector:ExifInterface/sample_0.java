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
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

    private static final String EXPLANATION =
            "The `android.media.ExifInterface` implementation has known security bugs "
            + "in older versions of Android. You should use the ExifInterface support "
            + "library instead, e.g. `androidx.exifinterface.media.ExifInterface` "
            + "(or `com.android.support.media.ExifInterface` in the legacy support library).";

    private static final String MESSAGE =
            "Avoid using `android.media.ExifInterface`; use the ExifInterface support library instead.";

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
                if (node.isConstructor()) {
                    UReferenceExpression classReference = node.getClassReference();
                    if (classReference != null
                            && isExifInterfaceClass(classReference.resolve())) {
                        reportUsage(node, context);
                    }
                } else {
                    PsiMethod method = (PsiMethod) node.resolve();
                    if (method != null
                            && isExifInterfaceClass(method.getContainingClass())) {
                        reportUsage(node, context);
                    }
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                if (isExifInterfaceClass(node.resolve())) {
                    reportUsage(node, context);
                }
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                UElement reference = node.getImportReference();
                if (reference instanceof UReferenceExpression
                        && isExifInterfaceClass(((UReferenceExpression) reference).resolve())) {
                    reportUsage(node, context);
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                UTypeReferenceExpression typeReference = node.getTypeReference();
                if (typeReference != null
                        && isExifInterfaceClass(typeReference.resolve())) {
                    reportUsage(node, context);
                }
            }
        };
    }

    private static boolean isExifInterfaceClass(PsiElement element) {
        return element instanceof PsiClass
                && ANDROID_MEDIA_EXIF_INTERFACE.equals(((PsiClass) element).getQualifiedName());
    }

    private static void reportUsage(UElement node, JavaContext context) {
        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
    }
}