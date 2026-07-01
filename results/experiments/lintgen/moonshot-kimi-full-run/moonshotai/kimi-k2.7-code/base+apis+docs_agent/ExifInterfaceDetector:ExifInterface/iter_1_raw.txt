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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
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
        return Arrays.asList(UImportStatement.class, UReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NotNull UImportStatement node) {
                checkReference(context, node);
            }

            @Override
            public void visitReferenceExpression(@NotNull UReferenceExpression node) {
                checkReference(context, node);
            }
        };
    }

    private static void checkReference(@NotNull JavaContext context, @NotNull UElement node) {
        PsiElement resolved = null;
        if (node instanceof UImportStatement) {
            resolved = ((UImportStatement) node).resolve();
        } else if (node instanceof UReferenceExpression) {
            resolved = ((UReferenceExpression) node).resolve();
        }

        PsiClass cls = null;
        if (resolved instanceof PsiClass) {
            cls = (PsiClass) resolved;
        } else if (resolved instanceof PsiMember) {
            cls = ((PsiMember) resolved).getContainingClass();
        }

        if (cls != null && OLD_EXIF_INTERFACE.equals(cls.getQualifiedName())) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
        }
    }
}