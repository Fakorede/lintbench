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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

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
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("ExifInterface");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement referenced) {
        if (referenced == null) {
            return;
        }

        PsiClass targetClass = null;
        if (referenced instanceof PsiClass) {
            targetClass = (PsiClass) referenced;
        } else if (referenced instanceof PsiMember) {
            targetClass = ((PsiMember) referenced).getContainingClass();
        }

        if (targetClass != null && "android.media.ExifInterface".equals(targetClass.getQualifiedName())) {
            context.report(
                    ISSUE,
                    reference,
                    context.getLocation(reference),
                    "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface` due to security vulnerabilities in older Android versions.");
        }
    }
}