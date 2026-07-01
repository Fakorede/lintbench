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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String NEW_EXIF_INTERFACE =
            "androidx.exifinterface.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "ExifInterface",
                            "Using `android.media.ExifInterface`",
                            "The `android.media.ExifInterface` implementation has some known "
                                    + "security bugs in older versions of Android. There is a new "
                                    + "implementation available of this library in the support "
                                    + "library, which is preferable.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private static final String MESSAGE =
            "Avoid using `android.media.ExifInterface`; use "
                    + "`androidx.exifinterface.media.ExifInterface` from the support library instead";

    public ExifInterfaceDetector() {}

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UImportStatement.class,
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UVariable.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new ExifInterfaceVisitor(context);
    }

    private static class ExifInterfaceVisitor extends AbstractUastVisitor {

        private final JavaContext mContext;

        ExifInterfaceVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitImportStatement(@NonNull UImportStatement node) {
            String importReference = node.getImportReference();
            if (importReference != null && importReference.startsWith(OLD_EXIF_INTERFACE)) {
                mContext.report(ISSUE, node, mContext.getLocation(node), MESSAGE);
            }
            return super.visitImportStatement(node);
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            PsiElement resolved = node.resolve();
            if (resolved != null) {
                String qualifiedName = getContainingClassName(resolved);
                if (OLD_EXIF_INTERFACE.equals(qualifiedName)) {
                    mContext.report(ISSUE, node, mContext.getLocation(node), MESSAGE);
                }
            } else {
                // Try to detect via class reference in the call
                String methodName = node.getMethodName();
                if (methodName != null) {
                    String receiverText = getReceiverText(node);
                    if (receiverText != null && receiverText.equals(OLD_EXIF_INTERFACE)) {
                        mContext.report(ISSUE, node, mContext.getLocation(node), MESSAGE);
                    }
                }
            }
            return super.visitCallExpression(node);
        }

        @Override
        public boolean visitQualifiedReferenceExpression(
                @NonNull UQualifiedReferenceExpression node) {
            String text = node.asSourceString();
            if (text != null && text.startsWith(OLD_EXIF_INTERFACE)) {
                PsiType type = node.getExpressionType();
                if (type != null && type.getCanonicalText().equals(OLD_EXIF_INTERFACE)) {
                    mContext.report(ISSUE, node, mContext.getLocation(node), MESSAGE);
                }
            }
            return super.visitQualifiedReferenceExpression(node);
        }

        @Override
        public boolean visitVariable(@NonNull UVariable node) {
            PsiType type = node.getType();
            String canonicalText = type.getCanonicalText();
            if (OLD_EXIF_INTERFACE.equals(canonicalText)) {
                mContext.report(ISSUE, node, mContext.getLocation(node), MESSAGE);
            }
            return super.visitVariable(node);
        }

        @Nullable
        private static String getContainingClassName(@NonNull PsiElement element) {
            PsiElement parent = element.getParent();
            while (parent != null) {
                if (parent instanceof com.intellij.psi.PsiClass) {
                    return ((com.intellij.psi.PsiClass) parent).getQualifiedName();
                }
                parent = parent.getParent();
            }
            return null;
        }

        @Nullable
        private static String getReceiverText(@NonNull UCallExpression node) {
            UElement receiver = node.getReceiver();
            if (receiver != null) {
                return receiver.asSourceString();
            }
            return null;
        }
    }
}