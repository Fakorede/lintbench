package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastUtils;

public class HandlerDetector extends Detector implements SourceCodeScanner {
    private static final String HANDLER_CLASS = "android.os.Handler";
    private static final String LOOPER_CLASS = "android.os.Looper";

    public static final Issue ISSUE_HANDLER_LEAK = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a `Looper` or `MessageQueue` for a thread other than the main thread, then there is no issue. If the `Handler` is using the `Looper` or `MessageQueue` of the main thread, you need to fix your `Handler` declaration, as follows: Declare the `Handler` as a static class; In the outer class, instantiate a `WeakReference` to the outer class and pass this object to your `Handler` when you instantiate the Handler; Make all references to members of the outer class using the `WeakReference` object.",
            Category.MESSAGES,
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (!context.getEvaluator().extendsClass(node, HANDLER_CLASS, false)) {
                    return;
                }

                if (node instanceof UAnonymousClass) {
                    checkHandler(context, node);
                } else if (isNonStaticInnerClass(node)) {
                    checkHandler(context, node);
                }
            }
        };
    }

    private boolean isNonStaticInnerClass(@NotNull UClass node) {
        PsiClass psi = node.getPsi();
        if (psi == null) {
            return false;
        }
        PsiClass containingClass = psi.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return !psi.hasModifierProperty(PsiModifier.STATIC);
    }

    private void checkHandler(@NotNull JavaContext context, @NotNull UClass node) {
        if (node instanceof UAnonymousClass) {
            UCallExpression newCall = UastUtils.getParentOfType(node, UCallExpression.class);
            if (newCall != null) {
                if (usesMainLooper(context, newCall)) {
                    report(context, node);
                    return;
                }
                if (hasExplicitLooper(context, newCall)) {
                    return;
                }
            }
            report(context, node);
            return;
        }

        if (hasUnsafeConstructor(context, node)) {
            report(context, node);
        }
    }

    private boolean hasUnsafeConstructor(@NotNull JavaContext context, @NotNull UClass node) {
        boolean foundConstructor = false;
        boolean foundSafeConstructor = false;

        for (UMethod method : node.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }

            foundConstructor = true;
            UCallExpression superCall = findSuperCall(context, method);

            if (superCall == null) {
                if (hasExplicitThisCall(method)) {
                    continue;
                }
                return true;
            }

            if (usesMainLooper(context, superCall)) {
                return true;
            }

            if (hasExplicitLooper(context, superCall)) {
                foundSafeConstructor = true;
                continue;
            }

            return true;
        }

        if (!foundConstructor) {
            return true;
        }

        return !foundSafeConstructor;
    }

    @Nullable
    private UCallExpression findSuperCall(@NotNull JavaContext context, @NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (!(body instanceof UBlockExpression)) {
            return null;
        }

        for (UExpression expr : ((UBlockExpression) body).getExpressions()) {
            if (expr instanceof UCallExpression) {
                UCallExpression call = (UCallExpression) expr;
                if (call.isConstructor()) {
                    PsiMethod resolved = (PsiMethod) call.resolve();
                    if (resolved != null
                            && context.getEvaluator().isMemberInClass(resolved, HANDLER_CLASS)) {
                        return call;
                    }
                }
            }
        }

        return null;
    }

    private boolean hasExplicitThisCall(@NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (!(body instanceof UBlockExpression)) {
            return false;
        }

        for (UExpression expr : ((UBlockExpression) body).getExpressions()) {
            if (expr instanceof UCallExpression && ((UCallExpression) expr).isConstructor()) {
                return true;
            }
        }

        return false;
    }

    private boolean usesMainLooper(@NotNull JavaContext context, @NotNull UCallExpression call) {
        for (UExpression arg : call.getValueArguments()) {
            if (arg instanceof UCallExpression) {
                UCallExpression argCall = (UCallExpression) arg;
                if ("getMainLooper".equals(argCall.getMethodName())) {
                    PsiMethod resolved = (PsiMethod) argCall.resolve();
                    if (resolved != null
                            && context.getEvaluator().isMemberInClass(resolved, LOOPER_CLASS)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasExplicitLooper(@NotNull JavaContext context, @NotNull UCallExpression call) {
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return false;
        }

        UExpression firstArg = args.get(0);
        PsiType type = firstArg.getExpressionType();
        return type != null && context.getEvaluator().extendsClass(type, LOOPER_CLASS);
    }

    private void report(@NotNull JavaContext context, @NotNull UClass node) {
        String name = node.getName();
        String message = name != null
                ? "Handler inner class \"" + name + "\" should be static or use a WeakReference to avoid memory leaks"
                : "Anonymous Handler should be static or use a WeakReference to avoid memory leaks";

        Location location = context.getNameLocation(node);
        context.report(ISSUE_HANDLER_LEAK, node, location, message);
    }
}