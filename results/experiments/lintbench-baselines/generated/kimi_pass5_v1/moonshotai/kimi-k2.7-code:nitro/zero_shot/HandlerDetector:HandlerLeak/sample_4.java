package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements SourceCodeScanner {
    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a `Looper` or `MessageQueue` for a thread other than the main thread, then there is no issue. If the `Handler` is using the `Looper` or `MessageQueue` of the main thread, you need to fix your `Handler` declaration, as follows: Declare the `Handler` as a static class; In the outer class, instantiate a `WeakReference` to the outer class and pass this object to your `Handler` when you instantiate the `Handler`; Make all references to members of the outer class using the `WeakReference` object.",
            Category.MEMORY,
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                checkClass(context, node);
            }
        };
    }

    private void checkClass(JavaContext context, UClass node) {
        if (!context.getEvaluator().extendsClass(node.getJavaPsi(), ANDROID_OS_HANDLER, false)) {
            return;
        }
        if (node.isInterface()) {
            return;
        }
        if (node.getJavaPsi().getContainingClass() == null) {
            return;
        }
        if (context.getEvaluator().isStaticInnerClass(node.getJavaPsi())) {
            return;
        }

        boolean hasConstructor = false;
        boolean hasMainLooperConstructor = false;
        for (UMethod method : node.getMethods()) {
            if (method.isConstructor()) {
                hasConstructor = true;
                if (constructorUsesMainLooper(context, method)) {
                    hasMainLooperConstructor = true;
                }
            }
        }

        if (!hasConstructor || hasMainLooperConstructor) {
            String message = "This Handler class should be static or leaks might occur ("
                    + ANDROID_OS_HANDLER + "). "
                    + "Declare it as a static class and use a WeakReference to the outer class "
                    + "for accessing outer members.";
            context.report(ISSUE, node, context.getNameLocation(node), message);
        }
    }

    private boolean constructorUsesMainLooper(JavaContext context, UMethod constructor) {
        final boolean[] usesMain = new boolean[1];
        constructor.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(UCallExpression node) {
                if (node.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
                    return super.visitCallExpression(node);
                }

                PsiMethod resolved = (PsiMethod) node.resolve();
                if (resolved == null) {
                    return super.visitCallExpression(node);
                }
                if (!context.getEvaluator().isMemberInSubClass(resolved, ANDROID_OS_HANDLER, false)) {
                    return super.visitCallExpression(node);
                }

                if (usesMainLooper(context, node)) {
                    usesMain[0] = true;
                }

                return super.visitCallExpression(node);
            }
        });
        return usesMain[0];
    }

    private static boolean usesMainLooper(JavaContext context, UCallExpression call) {
        List<UExpression> args = call.getValueArguments();
        boolean foundLooperArg = false;
        for (UExpression arg : args) {
            PsiType type = arg.getExpressionType();
            if (type != null && ANDROID_OS_LOOPER.equals(type.getCanonicalText())) {
                foundLooperArg = true;
                if (arg instanceof UCallExpression) {
                    UCallExpression argCall = (UCallExpression) arg;
                    PsiMethod resolved = (PsiMethod) argCall.resolve();
                    if (resolved != null
                            && "getMainLooper".equals(resolved.getName())
                            && context.getEvaluator().isMemberInSubClass(resolved, ANDROID_OS_LOOPER, false)) {
                        return true;
                    }
                }
                return false;
            }
        }

        return !foundLooperArg;
    }
}