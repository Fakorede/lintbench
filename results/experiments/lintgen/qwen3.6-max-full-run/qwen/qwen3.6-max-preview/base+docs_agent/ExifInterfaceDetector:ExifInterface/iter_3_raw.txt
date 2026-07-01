package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements JavaScanner {

    public static final Issue ISSUE = Issue.create(
        "ExifInterface",
        "Using `android.media.ExifInterface`",
        "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. There is a new implementation available of this library in the support library, which is preferable.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiReferenceExpression.class);
    }

    @Override
    public void visitJava(JavaContext context, PsiElement element) {
        PsiReferenceExpression reference = (PsiReferenceExpression) element;
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
            String qualifiedName = ((PsiClass) resolved).getQualifiedName();
            if ("android.media.ExifInterface".equals(qualifiedName)) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`");
            }
        }
    }
}