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
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastCallKind;

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

    private static final String EXIF_INTERFACE_CLASS = "android.media.ExifInterface";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UReferenceExpression.class,
                UImportStatement.class,
                UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                checkNode(context, node);
            }

            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                checkNode(context, node);
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                checkNode(context, node);
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                checkNode(context, node);
            }

            private void checkNode(@NonNull JavaContext context, @NonNull UElement node) {
                String qualifiedName = null;

                if (node instanceof UCallExpression) {
                    UCallExpression call = (UCallExpression) node;
                    if (call.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                        PsiMethod method = call.resolve();
                        if (method != null && method.getContainingClass() != null) {
                            qualifiedName = method.getContainingClass().getQualifiedName();
                        }
                    }
                } else if (node instanceof UReferenceExpression) {
                    PsiElement target = ((UReferenceExpression) node).resolve();
                    if (target instanceof PsiClass) {
                        qualifiedName = ((PsiClass) target).getQualifiedName();
                    }
                } else if (node instanceof UImportStatement) {
                    PsiElement target = ((UImportStatement) node).resolve();
                    if (target instanceof PsiClass) {
                        qualifiedName = ((PsiClass) target).getQualifiedName();
                    }
                } else if (node instanceof UVariable) {
                    PsiType type = ((UVariable) node).getType();
                    if (type != null) {
                        qualifiedName = type.getCanonicalText();
                    }
                }

                if (EXIF_INTERFACE_CLASS.equals(qualifiedName)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security vulnerabilities on older Android versions.");
                }
            }
        };
    }
}