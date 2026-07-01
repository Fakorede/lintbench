package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UResolvable;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. "
                    + "There is a new implementation available of this library in the support library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UQualifiedReferenceExpression.class,
                UCallExpression.class,
                UVariable.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                if (EXIF_INTERFACE.equals(node.getQualifiedName())) {
                    report(context, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                if (isExifInterface(node)) {
                    report(context, node);
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (isExifInterface(node)) {
                    report(context, node);
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                if (isExifInterface(node)) {
                    report(context, node);
                }
            }
        };
    }

    private boolean isExifInterface(UElement element) {
        if (element instanceof UResolvable) {
            PsiElement resolved = ((UResolvable) element).resolve();
            if (resolved instanceof PsiClass) {
                return EXIF_INTERFACE.equals(((PsiClass) resolved).getQualifiedName());
            } else if (resolved instanceof PsiMethod) {
                PsiClass cls = ((PsiMethod) resolved).getContainingClass();
                return cls != null && EXIF_INTERFACE.equals(cls.getQualifiedName());
            } else if (resolved instanceof PsiField) {
                PsiClass cls = ((PsiField) resolved).getContainingClass();
                return cls != null && EXIF_INTERFACE.equals(cls.getQualifiedName());
            }
        }
        if (element instanceof UVariable) {
            PsiType type = ((UVariable) element).getType();
            if (type != null) {
                return EXIF_INTERFACE.equals(type.getCanonicalText());
            }
        }
        return false;
    }

    private void report(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` due to known security bugs");
    }
}