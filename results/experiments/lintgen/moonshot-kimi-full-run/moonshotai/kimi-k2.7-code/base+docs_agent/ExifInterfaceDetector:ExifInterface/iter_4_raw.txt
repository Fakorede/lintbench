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
import com.intellij.psi.PsiMember;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class ExifInterfaceDetector extends Detector implements Detector.UastScanner {

    private static final String PLATFORM_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String SUPPORT_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    private static final String MESSAGE =
            "Use `" + SUPPORT_EXIF_INTERFACE + "` from the support library instead of `"
                    + PLATFORM_EXIF_INTERFACE + "`";

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known security bugs "
                            + "in older versions of Android. There is a new implementation available "
                            + "of this library in the support library, which is preferable.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UQualifiedReferenceExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NotNull UImportStatement node) {
                check(node, node.resolve());
            }

            @Override
            public void visitQualifiedReferenceExpression(
                    @NotNull UQualifiedReferenceExpression node) {
                UElement parent = node.getUastParent();
                if (parent instanceof UQualifiedReferenceExpression
                        && node == ((UQualifiedReferenceExpression) parent).getReceiver()) {
                    return;
                }
                check(node, node.resolve());
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NotNull USimpleNameReferenceExpression node) {
                if (node.getUastParent() instanceof UQualifiedReferenceExpression) {
                    return;
                }
                check(node, node.resolve());
            }

            private void check(@NotNull UElement node, PsiElement resolved) {
                if (resolved instanceof PsiClass) {
                    String qualifiedName = ((PsiClass) resolved).getQualifiedName();
                    if (PLATFORM_EXIF_INTERFACE.equals(qualifiedName)) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                    }
                } else if (resolved instanceof PsiMember) {
                    PsiClass containingClass = ((PsiMember) resolved).getContainingClass();
                    if (containingClass != null
                            && PLATFORM_EXIF_INTERFACE.equals(containingClass.getQualifiedName())) {
                        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                    }
                }
            }
        };
    }
}