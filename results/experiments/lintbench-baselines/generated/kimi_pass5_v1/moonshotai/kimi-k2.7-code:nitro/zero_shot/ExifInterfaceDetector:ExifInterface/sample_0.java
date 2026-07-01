package com.android.tools.lint.checks;

import androidx.annotation.NonNull;
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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {
    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression node,
            @NonNull PsiElement resolved) {
        if (resolved instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) resolved;
            if (ANDROID_MEDIA_EXIF_INTERFACE.equals(psiClass.getQualifiedName())) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Using android.media.ExifInterface has known security issues on older Android versions; "
                                + "use the support library ExifInterface instead.");
            }
        }
    }

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has known security bugs in older versions "
                    + "of Android. Use the support library implementation instead.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));
}