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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.UVariable;
import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String EXIF_INTERFACE_CLASS = "android.media.ExifInterface";
    private static final String MESSAGE = "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. "
                    + "There is a new implementation available of this library in the support library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UQualifiedReferenceExpression.class,
                UCallExpression.class,
                UVariable.class
        );
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                if (node.getImportReference() != null
                        && EXIF_INTERFACE_CLASS.equals(node.getImportReference().getQualifiedName())) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                String qName = node.getQualifiedName();
                if (qName != null && (qName.equals(EXIF_INTERFACE_CLASS) || qName.startsWith(EXIF_INTERFACE_CLASS + "."))) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (node.isConstructorCall() && "ExifInterface".equals(node.getMethodName())) {
                    PsiMethod method = node.resolve();
                    if (method != null) {
                        PsiClass containingClass = method.getContainingClass();
                        if (containingClass != null && EXIF_INTERFACE_CLASS.equals(containingClass.getQualifiedName())) {
                            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                        }
                    }
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                UTypeReferenceExpression typeRef = node.getTypeReference();
                if (typeRef != null && EXIF_INTERFACE_CLASS.equals(typeRef.getQualifiedName())) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }
        };
    }
}