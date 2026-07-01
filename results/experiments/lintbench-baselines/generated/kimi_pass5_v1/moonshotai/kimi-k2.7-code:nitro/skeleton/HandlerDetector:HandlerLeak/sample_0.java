package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final String HANDLER_CLASS = "android.os.Handler";
    private static final String GET_MAIN_LOOPER = "getMainLooper";

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the "
                            + "outer class from being garbage collected. If the Handler is "
                            + "using a `Looper` or `MessageQueue` for a thread other than the "
                            + "main thread, then there is no issue. If the Handler is using "
                            + "the `Looper` or `MessageQueue` of the main thread, you need to "
                            + "fix your `Handler` declaration, as follows: Declare the `Handler` "
                            + "as a static class; in the outer class, instantiate a "
                            + "`WeakReference` to the outer class and pass this object to your "
                            + "`Handler` when you instantiate the `Handler`; make all references "
                            + "to members of the outer class using the `WeakReference` object.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HANDLER_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            checkClass(context, declaration);
            return;
        }

        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }

        if (psiClass.hasModifierProperty(PsiModifier.STATIC)
                || psiClass.getContainingClass() == null) {
            return;
        }

        checkClass(context, declaration);
    }

    private void checkClass(JavaContext context, UClass declaration) {
        boolean hasExplicitConstructor = false;
        boolean mayLeak = false;

        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor()) {
                hasExplicitConstructor = true;
                if (constructorMayUseMainThread(method)) {
                    mayLeak = true;
                    break;
                }
            }
        }

        if (!hasExplicitConstructor || mayLeak) {
            report(context, declaration);
        }
    }

    private boolean constructorMayUseMainThread(UMethod constructor) {
        UBlockExpression body = constructor.getUastBody();
        if (body == null) {
            return true;
        }

        List<UExpression> expressions = body.getExpressions();
        if (!expressions.isEmpty()) {
            UExpression first = expressions.get(0);
            if (first instanceof UCallExpression) {
                UCallExpression call = (UCallExpression) first;
                if (call.isSuperCall()) {
                    return isMainThreadConstructor(call);
                }
            }
        }

        return true;
    }

    private boolean isMainThreadConstructor(UCallExpression superCall) {
        List<UExpression> args = superCall.getValueArguments();
        if (args.isEmpty()) {
            return true;
        }

        UExpression firstArg = args.get(0);
        if (isMainLooperExpression(firstArg)) {
            return true;
        }

        if (args.size() == 1) {
            return true;
        }

        return false;
    }

    private boolean isMainLooperExpression(UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            return GET_MAIN_LOOPER.equals(call.getMethodName());
        }
        return false;
    }

    private void report(JavaContext context, UElement node) {
        String message = "This Handler class should be static or leaks may occur";
        context.report(ISSUE, node, context.getNameLocation(node), message);
    }
}