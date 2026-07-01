package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. " +
            "There is a new implementation available of this library in the support library, which is preferable.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public void visitReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node) {
        PsiElement resolved = node.resolve();
        PsiClass targetClass = null;
        if (resolved instanceof PsiClass) {
            targetClass = (PsiClass) resolved;
        } else if (resolved instanceof PsiMember) {
            targetClass = ((PsiMember) resolved).getContainingClass();
        }

        if (targetClass != null && "android.media.ExifInterface".equals(targetClass.getQualifiedName())) {
            Location location = context.getLocation(node);
            context.report(ISSUE, location, "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` to avoid security vulnerabilities on older Android versions.");
        }
    }
}