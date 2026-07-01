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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
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
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String EXIF_INTERFACE_OLD = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue EXIF_INTERFACE =
            Issue.create(
                            "ExifInterface",
                            "Using android.media.ExifInterface",
                            "The `android.media.ExifInterface` implementation has known security "
                                    + "bugs in older versions of Android. Use the ExifInterface "
                                    + "support library instead.",
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

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new ExifInterfaceHandler(context);
    }

    private static class ExifInterfaceHandler extends UElementHandler {
        private final JavaContext mContext;

        ExifInterfaceHandler(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            PsiMethod method = node.resolve();
            if (method != null && isExifInterface(method.getContainingClass())) {
                report(node);
            }
        }

        @Override
        public void visitQualifiedReferenceExpression(
                @NonNull UQualifiedReferenceExpression node) {
            UElement parent = node.getUastParent();
            if (parent instanceof UCallExpression || parent instanceof UImportStatement) {
                return;
            }
            if (parent instanceof UTypeReferenceExpression) {
                UElement grandparent = parent.getUastParent();
                if (grandparent instanceof UVariable) {
                    return;
                }
            }

            PsiElement resolved = node.resolve();
            PsiClass cls = null;
            if (resolved instanceof PsiClass) {
                cls = (PsiClass) resolved;
            } else if (resolved instanceof PsiMember) {
                cls = ((PsiMember) resolved).getContainingClass();
            }
            if (isExifInterface(cls)) {
                report(node);
            }
        }

        @Override
        public void visitImportStatement(@NonNull UImportStatement node) {
            PsiElement sourcePsi = node.getSourcePsi();
            if (sourcePsi instanceof PsiImportStatementBase) {
                PsiJavaCodeReferenceElement reference =
                        ((PsiImportStatementBase) sourcePsi).getImportReference();
                if (reference != null) {
                    PsiElement resolved = reference.resolve();
                    if (resolved instanceof PsiClass && isExifInterface((PsiClass) resolved)) {
                        report(node);
                    }
                }
            }
        }

        @Override
        public void visitVariable(@NonNull UVariable node) {
            PsiType type = node.getType();
            if (type instanceof PsiClassType) {
                PsiClass resolved = ((PsiClassType) type).resolve();
                if (isExifInterface(resolved)) {
                    report(node);
                }
            }
        }

        private void report(@NonNull UElement node) {
            mContext.report(
                    EXIF_INTERFACE,
                    node,
                    mContext.getLocation(node),
                    "Using `android.media.ExifInterface` is discouraged; use the ExifInterface support library instead.");
        }

        private static boolean isExifInterface(@Nullable PsiClass psiClass) {
            return psiClass != null && EXIF_INTERFACE_OLD.equals(psiClass.getQualifiedName());
        }
    }
}