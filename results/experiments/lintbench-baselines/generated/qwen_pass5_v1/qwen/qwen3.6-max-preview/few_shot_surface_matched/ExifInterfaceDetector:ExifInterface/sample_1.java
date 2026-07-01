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
import com.intellij.psi.PsiType;
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

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
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
                UVariable.class,
                UQualifiedReferenceExpression.class,
                UCallExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                String qualifiedName = node.getQualifiedName();
                if (EXIF_INTERFACE.equals(qualifiedName)) {
                    report(context, node);
                }
            }

            @Override
            public void visitVariable(@NonNull UVariable node) {
                UTypeReferenceExpression typeRef = node.getTypeReference();
                if (typeRef != null && EXIF_INTERFACE.equals(typeRef.getQualifiedName())) {
                    report(context, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
                PsiType type = context.getEvaluator().getType(node.getQualifier());
                if (type != null && EXIF_INTERFACE.equals(type.getCanonicalText())) {
                    report(context, node);
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (node.isConstructorCall()) {
                    UTypeReferenceExpression typeRef = node.getTypeReference();
                    if (typeRef != null && EXIF_INTERFACE.equals(typeRef.getQualifiedName())) {
                        report(context, node);
                    }
                }
            }

            private void report(@NonNull JavaContext context, @NonNull UElement node) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` "
                                + "to avoid security bugs on older Android versions.");
            }
        };
    }
}