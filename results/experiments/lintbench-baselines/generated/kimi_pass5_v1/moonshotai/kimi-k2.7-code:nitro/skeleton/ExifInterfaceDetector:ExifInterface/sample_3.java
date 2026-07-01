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
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

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
        return Arrays.asList(
                UCallExpression.class,
                UImportStatement.class,
                UQualifiedReferenceExpression.class,
                UVariable.class
        );
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (!node.isConstructorCall()) {
                    return;
                }
                UReferenceExpression classReference = node.getClassReference();
                if (classReference == null) {
                    return;
                }
                PsiElement resolved = classReference.resolve();
                if (resolved instanceof PsiClass && isExifInterface((PsiClass) resolved)) {
                    report(context, node);
                }
            }

            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                UElement importReference = node.getImportReference();
                if (!(importReference instanceof UReferenceExpression)) {
                    return;
                }
                PsiElement resolved = ((UReferenceExpression) importReference).resolve();
                if (resolved instanceof PsiClass && isExifInterface((PsiClass) resolved)) {
                    report(context, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(
                    @NonNull UQualifiedReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass && isExifInterface((PsiClass) resolved)) {
                    report(context, node);
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                PsiType type = node.getType();
                if (type == null) {
                    return;
                }
                PsiClass typeClass = context.getEvaluator().getTypeClass(type);
                if (isExifInterface(typeClass)) {
                    report(context, node);
                }
            }

            private boolean isExifInterface(PsiClass cls) {
                return cls != null && EXIF_INTERFACE.equals(cls.getQualifiedName());
            }

            private void report(@NonNull JavaContext context, @NonNull UElement node) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `android.media.ExifInterface` is not recommended; "
                                + "use the support library ExifInterface instead");
            }
        };
    }
}