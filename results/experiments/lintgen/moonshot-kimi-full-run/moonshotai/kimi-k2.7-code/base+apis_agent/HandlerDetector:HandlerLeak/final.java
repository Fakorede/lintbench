package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";
    private static final String ISSUE_ID = "HandlerLeak";

    private static final String DESCRIPTION = "Handler reference leaks";

    private static final String EXPLANATION =
            "Since this Handler is declared as an inner class, it may prevent the outer class "
                    + "from being garbage collected. If the Handler is using a `Looper` or "
                    + "`MessageQueue` for a thread other than the main thread, then there is no "
                    + "issue. If the `Handler` is using the `Looper` or `MessageQueue` of the main "
                    + "thread, you need to fix your `Handler` declaration, as follows: Declare the "
                    + "`Handler` as a static class; in the outer class, instantiate a "
                    + "`WeakReference` to the outer class and pass this object to your `Handler` "
                    + "when you instantiate the `Handler`; make all references to members of the "
                    + "outer class using the `WeakReference` object.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            DESCRIPTION,
            EXPLANATION,
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                checkClass(context, node);
            }
        };
    }

    private void checkClass(@NotNull JavaContext context, @NotNull UClass node) {
        if (node.isInterface()) {
            return;
        }

        if (node.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiClass containingClass = node.getContainingClass();
        if (containingClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(node, ANDROID_OS_HANDLER, false)) {
            return;
        }

        if (node instanceof UAnonymousClass) {
            UCallExpression parentCall = UastUtils.getParentOfType(node, UCallExpression.class, false);
            if (parentCall != null && isDefaultOrMainLooper(context, parentCall)) {
                report(context, node, parentCall);
            }
            return;
        }

        boolean hasConstructor = false;
        for (UMethod method : node.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }
            hasConstructor = true;

            UCallExpression superCall = findSuperCall(method, node);
            if (superCall != null) {
                if (isDefaultOrMainLooper(context, superCall)) {
                    report(context, node, null);
                    return;
                }
                continue;
            }

            if (hasThisCall(method, node)) {
                continue;
            }

            report(context, node, null);
            return;
        }

        if (!hasConstructor) {
            report(context, node, null);
        }
    }

    private void report(@NotNull JavaContext context, @NotNull UClass node, UCallExpression call) {
        Location location;
        if (call != null) {
            location = context.getLocation(call);
        } else {
            location = context.getNameLocation(node);
        }
        context.report(ISSUE, node, location, EXPLANATION);
    }

    private UCallExpression findSuperCall(@NotNull UMethod constructor, @NotNull UClass cls) {
        UExpression body = constructor.getUastBody();
        if (body == null) {
            return null;
        }

        PsiClass superClass = cls.getSuperClass();
        if (superClass == null) {
            return null;
        }

        final UCallExpression[] result = new UCallExpression[1];
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(@NotNull UCallExpression node) {
                if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                    PsiMethod resolved = node.resolve();
                    if (resolved != null && resolved.isConstructor()) {
                        PsiClass containing = resolved.getContainingClass();
                        if (superClass.equals(containing)) {
                            result[0] = node;
                            return true;
                        }
                    }
                }
                return super.visitCallExpression(node);
            }
        });

        return result[0];
    }

    private boolean hasThisCall(@NotNull UMethod constructor, @NotNull UClass cls) {
        UExpression body = constructor.getUastBody();
        if (body == null) {
            return false;
        }

        final boolean[] result = new boolean[1];
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(@NotNull UCallExpression node) {
                if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                    PsiMethod resolved = node.resolve();
                    if (resolved != null && resolved.isConstructor()) {
                        PsiClass containing = resolved.getContainingClass();
                        if (cls.equals(containing)) {
                            result[0] = true;
                            return true;
                        }
                    }
                }
                return super.visitCallExpression(node);
            }
        });

        return result[0];
    }

    private boolean isDefaultOrMainLooper(@NotNull JavaContext context, @NotNull UCallExpression call) {
        PsiMethod method = call.resolve();
        List<UExpression> args = call.getValueArguments();

        if (method == null || !method.isConstructor()) {
            return args.isEmpty();
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiType paramType = parameters[i].getType();
            if (ANDROID_OS_LOOPER.equals(paramType.getCanonicalText())) {
                if (i >= args.size()) {
                    return true;
                }
                UExpression arg = args.get(i);
                return arg == null || isMainLooperReference(context, arg);
            }
        }

        return true;
    }

    private boolean isMainLooperReference(@NotNull JavaContext context, @NotNull UExpression expression) {
        if (!(expression instanceof UCallExpression)) {
            return false;
        }

        PsiMethod method = ((UCallExpression) expression).resolve();
        if (method == null) {
            return false;
        }

        return "getMainLooper".equals(method.getName())
                && context.getEvaluator().isMemberInClass(method, ANDROID_OS_LOOPER);
    }
}