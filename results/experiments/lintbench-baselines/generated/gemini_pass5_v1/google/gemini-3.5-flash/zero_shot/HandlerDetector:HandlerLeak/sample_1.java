package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UastUtils;

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
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Handler");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();

        // Only care about inner classes (which have an outer enclosing class)
        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass == null) {
            return;
        }

        // If it's static, there's no implicit reference to the outer class, so no leak.
        if (evaluator.isStatic(declaration)) {
            return;
        }

        // Check if it is an anonymous inner class
        if (declaration instanceof UAnonymousClass) {
            UAnonymousClass anonymousClass = (UAnonymousClass) declaration;
            UObjectLiteralExpression expression = UastUtils.getParentOfType(anonymousClass, UObjectLiteralExpression.class);
            if (expression != null) {
                List<UExpression> arguments = expression.getValueArguments();
                if (hasNonMainLooper(arguments)) {
                    return;
                }
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This `Handler` class should be static or leaks might occur"
        );
    }

    private boolean hasNonMainLooper(List<UExpression> arguments) {
        for (UExpression argument : arguments) {
            PsiType type = argument.getExpressionType();
            if (type != null && type.getCanonicalText().equals("android.os.Looper")) {
                if (isMainLooper(argument)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean isMainLooper(UExpression argument) {
        if (argument instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) argument;
            String methodName = call.getMethodName();
            if ("getMainLooper".equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}