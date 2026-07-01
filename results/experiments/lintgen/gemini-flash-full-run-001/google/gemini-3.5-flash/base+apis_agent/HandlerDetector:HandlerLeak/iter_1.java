package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UDeclaration;
import org.w3c.dom.Node;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastUtils;
import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "HandlerLeak",
        "Handler reference leaks",
        "Since this Handler is declared as an inner class, it may prevent the " +
        "outer class from being garbage collected. If the Handler is " +
        "using a `Looper` or `MessageQueue` for a thread other than the " +
        "main thread, then there is no issue. If the `Handler` is using " +
        "the `Looper` or `MessageQueue` of the main thread, you need to " +
        "fix your `Handler` declaration, as follows: Declare the " +
        "`Handler` as a static class; In the outer class, instantiate a " +
        "`WeakReference` to the outer class and pass this object to your " +
        "`Handler` when you instantiate the `Handler`; Make all " +
        "references to members of the outer class using the " +
        "`WeakReference` object.",
        Category.PERFORMANCE,
        6,
        Severity.WARNING,
        new Implementation(
            HandlerDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Handler");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (context.getEvaluator().isStatic(declaration)) {
            return;
        }

        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass == null) {
            return;
        }

        UMethod containingMethod = UastUtils.getContainingUMethod(declaration);
        if (containingMethod != null && context.getEvaluator().isStatic(containingMethod)) {
            return;
        }

        Location location = context.getNameLocation((UDeclaration) declaration);
        if (location == Location.NONE) {
            location = context.getLocation((UElement) declaration);
        }

        context.report(
            ISSUE,
            (UElement) declaration,
            location,
            "This `Handler` class should be static or leaks might occur"
        );
    }
}