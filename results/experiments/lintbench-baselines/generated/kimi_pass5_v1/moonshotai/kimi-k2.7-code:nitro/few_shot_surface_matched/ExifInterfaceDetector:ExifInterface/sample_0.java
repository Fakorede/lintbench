package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIFINTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "ExifInterface",
                            "Using android.media.ExifInterface",
                            "The `android.media.ExifInterface` implementation has some known "
                                    + "security bugs in older versions of Android. There is a new "
                                    + "implementation available in the support library, which is "
                                    + "preferable.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ExifInterfaceDetector() {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UImportStatement.class,
                UVariable.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new ExifInterfaceVisitor(context);
    }

    private static boolean isAndroidMediaExifInterface(PsiClass psiClass) {
        return psiClass != null && ANDROID_MEDIA_EXIFINTERFACE.equals(psiClass.getQualifiedName());
    }

    private static void report(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `android.media.ExifInterface` is not recommended; use the support "
                        + "library version instead.");
    }

    private static class ExifInterfaceVisitor extends UElementHandler {
        private final JavaContext mContext;

        ExifInterfaceVisitor(JavaContext context) {
            this.mContext = context;
        }

        @Override
        public void visitCallExpression(UCallExpression node) {
            PsiMethod method = node.resolve();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                if (isAndroidMediaExifInterface(containingClass)) {
                    report(mContext, node);
                }
            }
        }

        @Override
        public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
            UElement parent = node.getUastParent();
            if (parent instanceof UCallExpression
                    || parent instanceof UVariable
                    || parent instanceof UImportStatement) {
                return;
            }

            PsiElement resolved = node.resolve();
            if (resolved instanceof PsiClass) {
                if (isAndroidMediaExifInterface((PsiClass) resolved)) {
                    report(mContext, node);
                }
            } else if (resolved instanceof PsiMember) {
                if (isAndroidMediaExifInterface(((PsiMember) resolved).getContainingClass())) {
                    report(mContext, node);
                }
            }
        }

        @Override
        public void visitImportStatement(UImportStatement node) {
            PsiElement resolved = node.resolve();
            if (resolved instanceof PsiClass
                    && isAndroidMediaExifInterface((PsiClass) resolved)) {
                report(mContext, node);
            }
        }

        @Override
        public void visitVariable(UVariable node) {
            PsiType type = node.getType();
            if (type instanceof PsiClassType) {
                PsiClass resolved = ((PsiClassType) type).resolve();
                if (isAndroidMediaExifInterface(resolved)) {
                    report(mContext, node);
                }
            }
        }
    }
}