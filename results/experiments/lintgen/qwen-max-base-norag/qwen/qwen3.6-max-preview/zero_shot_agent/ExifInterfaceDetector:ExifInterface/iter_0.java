package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
        new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String NEW_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitReference(@NotNull UContext context, @NotNull UReferenceExpression reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) resolved;
            if (OLD_EXIF_INTERFACE.equals(psiClass.getQualifiedName())) {
                String message = "Use `" + NEW_EXIF_INTERFACE + "` instead of `" + OLD_EXIF_INTERFACE + "`";
                context.report(ISSUE, context.getLocation(reference), message);
            }
        }
    }
}