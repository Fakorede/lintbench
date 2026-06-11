package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.ClassScanner {
    public static final Issue ISSUE = Issue.create(
            "ExifInterfaceUsage",
            "Using `android.media.ExifInterface` has known security bugs in older versions of Android.",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. There is a new implementation available of this library in the support library, which is preferable.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    ExifInterfaceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("android.media.ExifInterface");
    }

    @Override
    public boolean visitsClassMembers() {
        return true;
    }

    @Override
    public void visitClassContext(JavaContext context, UClass klass) {
        for (UMethod method : klass.getMethods()) {
            if ("android.media.ExifInterface".equals(method.getName())) {
                context.report(ISSUE, method, context.getLocation(method), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
            }
        }
    }

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.media.ExifInterface");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        context.report(ISSUE, node, context.getLocation(node), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
    }
}