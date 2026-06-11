package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.JavaScanner {
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
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if ("android.media.ExifInterface".equals(method.getContainingClass().getQualifiedName())) {
            context.report(ISSUE, node, context.getLocation(node), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
        }
    }

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.media.ExifInterface");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        if ("ExifInterface".equals(constructor.getName())) {
            context.report(ISSUE, node, context.getLocation(node), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null && "android.media.ExifInterface".equals(method.getContainingClass().getQualifiedName())) {
                    context.report(ISSUE, node, context.getLocation(node), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
                }
                return super.visitCallExpression(node);
            }

            @Override
            public boolean visitConstructor(UCallExpression node) {
                PsiMethod constructor = node.resolve();
                if (constructor != null && "ExifInterface".equals(constructor.getName())) {
                    context.report(ISSUE, node, context.getLocation(node), "Using `android.media.ExifInterface` has known security bugs in older versions of Android.");
                }
                return super.visitConstructor(node);
            }
        };
    }
}