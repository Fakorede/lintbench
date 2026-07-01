package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UParameter;

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
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.os.Handler", true)) {
                    return;
                }

                PsiClass psiClass = node.getJavaPsi();
                if (psiClass == null) {
                    return;
                }

                PsiClass containingClass = psiClass.getContainingClass();
                if (containingClass == null) {
                    return; // Not an inner class
                }

                if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
                    return; // Static inner class is fine
                }

                if (node instanceof UAnonymousClass) {
                    UElement parent = node.getUastParent();
                    if (parent instanceof UObjectLiteralExpression) {
                        UObjectLiteralExpression literal = (UObjectLiteralExpression) parent;
                        List<UExpression> valueArguments = literal.getValueArguments();
                        if (hasLooperArgument(valueArguments, evaluator)) {
                            return; // Safe
                        }
                    }
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "This Handler class should be static or leaks might occur");
                    return;
                }

                // Named inner class
                boolean hasLooperConstructor = false;
                for (UMethod method : node.getMethods()) {
                    if (method.isConstructor()) {
                        for (UParameter parameter : method.getUastParameters()) {
                            PsiType type = parameter.getType();
                            if (evaluator.typeMatches(type, "android.os.Looper")) {
                                hasLooperConstructor = true;
                                break;
                            }
                        }
                    }
                    if (hasLooperConstructor) {
                        break;
                    }
                }

                if (!hasLooperConstructor) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "This Handler class should be static or leaks might occur");
                }
            }
        };
    }

    private boolean hasLooperArgument(List<UExpression> arguments, JavaEvaluator evaluator) {
        for (UExpression argument : arguments) {
            PsiType type = argument.getExpressionType();
            if (type != null && evaluator.typeMatches(type, "android.os.Looper")) {
                if (isMainLooper(argument)) {
                    continue; // Main looper still leaks
                }
                return true;
            }
        }
        return false;
    }

    private boolean isMainLooper(UExpression argument) {
        if (argument instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) argument;
            if ("getMainLooper".equals(call.getMethodName())) {
                PsiMethod resolved = call.resolve();
                if (resolved != null) {
                    PsiClass containingClass = resolved.getContainingClass();
                    if (containingClass != null && "android.os.Looper".equals(containingClass.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}