package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.UastScanner {

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String NEW_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
        "ExifInterface",
        "Using `android.media.ExifInterface`",
        "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. " +
        "There is a new implementation available of this library in the support library, which is preferable.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression node, @NonNull PsiElement referenced) {
        PsiClass psiClass = null;
        if (referenced instanceof PsiClass) {
            psiClass = (PsiClass) referenced;
        } else if (referenced instanceof PsiMember) {
            psiClass = ((PsiMember) referenced).getContainingClass();
        }

        if (psiClass != null && OLD_EXIF_INTERFACE.equals(psiClass.getQualifiedName())) {
            context.report(ISSUE, node, context.getLocation(node),
                "Use `" + NEW_EXIF_INTERFACE + "` instead of `" + OLD_EXIF_INTERFACE + "` " +
                "to avoid security vulnerabilities on older Android versions.");
        }
    }
}