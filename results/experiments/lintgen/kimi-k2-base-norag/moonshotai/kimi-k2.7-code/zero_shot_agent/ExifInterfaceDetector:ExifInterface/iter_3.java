package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaPsiScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements JavaPsiScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. There is a new implementation available of this library in the support library, which is preferable.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiImportStatement.class);
    }

    @Override
    public void visitElement(@NonNull JavaContext context, @NonNull PsiElement element) {
        PsiImportStatement statement = (PsiImportStatement) element;
        PsiJavaCodeReferenceElement reference = statement.getImportReference();
        if (reference != null) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiClass) {
                PsiClass cls = (PsiClass) resolved;
                if ("android.media.ExifInterface".equals(cls.getQualifiedName())) {
                    context.report(
                            ISSUE,
                            statement,
                            context.getLocation(statement),
                            "Use `androidx.exifinterface.media.ExifInterface` from the support library instead of `android.media.ExifInterface`");
                }
            }
        }
    }
}