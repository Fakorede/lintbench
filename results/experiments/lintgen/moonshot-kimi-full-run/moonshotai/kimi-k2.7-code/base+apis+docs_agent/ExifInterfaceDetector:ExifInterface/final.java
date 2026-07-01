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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String MESSAGE =
            "Using `android.media.ExifInterface`; use the support library ExifInterface instead.";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",
            "The `android.media.ExifInterface` implementation has known security bugs in older "
                    + "versions of Android. Prefer the support library implementation.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NotNull UImportStatement node) {
                PsiElement resolved = node.resolve();
                if (resolved != null && isOldExif(resolved)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }
        };
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitReference(
            @NotNull JavaContext context,
            @NotNull UReferenceExpression reference,
            @NotNull PsiElement referenced) {
        if (!isOldExif(referenced)) {
            return;
        }

        UElement parent = reference.getUastParent();
        while (parent != null) {
            if (parent instanceof UImportStatement) {
                return;
            }
            parent = parent.getUastParent();
        }

        UElement directParent = reference.getUastParent();
        if (directParent instanceof UCallExpression) {
            PsiMethod method = ((UCallExpression) directParent).resolve();
            if (method != null && method.isConstructor()) {
                return;
            }
        }

        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE);
    }

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(OLD_EXIF_INTERFACE);
    }

    @Override
    public void visitConstructor(
            @NotNull JavaContext context,
            @NotNull UCallExpression node,
            @NotNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass != null && OLD_EXIF_INTERFACE.equals(containingClass.getQualifiedName())) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
        }
    }

    private static boolean isOldExif(@Nullable PsiElement element) {
        if (element instanceof PsiClass) {
            return OLD_EXIF_INTERFACE.equals(((PsiClass) element).getQualifiedName());
        } else if (element instanceof PsiMember) {
            PsiClass containingClass = ((PsiMember) element).getContainingClass();
            return containingClass != null && OLD_EXIF_INTERFACE.equals(containingClass.getQualifiedName());
        }
        return false;
    }
}