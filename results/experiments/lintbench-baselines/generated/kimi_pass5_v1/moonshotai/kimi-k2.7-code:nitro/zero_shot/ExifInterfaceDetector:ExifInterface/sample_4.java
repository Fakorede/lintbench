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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String CLASS_EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",
            "The `android.media.ExifInterface` implementation has some known "
                    + "security bugs in older versions of Android. There is a new "
                    + "implementation available of this library in the support "
                    + "library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(UReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) resolved;
                    if (CLASS_EXIF_INTERFACE.equals(psiClass.getQualifiedName())) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `android.support.media.ExifInterface` from the support library instead of `android.media.ExifInterface`"
                        );
                    }
                }
            }
        };
    }
}