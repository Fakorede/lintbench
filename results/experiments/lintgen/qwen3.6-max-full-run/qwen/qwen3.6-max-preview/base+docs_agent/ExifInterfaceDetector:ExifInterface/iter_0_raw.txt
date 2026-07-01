package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "ExifInterface",
        "Using `android.media.ExifInterface`",
        "The `android.media.ExifInterface` implementation has some known  security bugs in older versions of Android. There is a new  implementation available of this library in the support  library, which is preferable.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                String identifier = node.getIdentifier();
                if (!"ExifInterface".equals(identifier)) {
                    return;
                }

                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiClass) {
                    String qualifiedName = ((PsiClass) resolved).getQualifiedName();
                    if ("android.media.ExifInterface".equals(qualifiedName)) {
                        context.report(ISSUE, node, context.getLocation(node),
                            "Use `androidx.exifinterface.media.ExifInterface` instead of `android.media.ExifInterface`");
                    }
                }
            }
        };
    }
}