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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HandlerDetector extends Detector implements SourceCodeScanner {
    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";
    private static final String ANDROID_OS_HANDLER_CALLBACK = "android.os.Handler$Callback";

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this Handler is declared as an inner class, it may prevent the "
                    + "outer class from being garbage collected. If the Handler is "
                    + "using a `Looper` or `MessageQueue` for a thread other than the "
                    + "main thread, then there is no issue. If the `Handler` is using "
                    + "the `Looper` or `MessageQueue` of the main thread, you need to "
                    + "fix your `Handler` declaration, as follows: Declare the `Handler` "
                    + "as a static class; In the outer class, instantiate a "
                    + "`WeakReference` to the outer class and pass this object to your "
                    + "`Handler` when you instantiate the `Handler`; Make all references "
                    + "to members of the outer class using the `WeakReference` object.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (context.isSuppressed(ISSUE, node)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node.getPsi(), ANDROID_OS_HANDLER, false)) {
                    return;
                }

                if (node.getContainingClass() == null
                        || node.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }

                if (usesMainThreadLooper(node)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "This Handler class should be static or leaks may occur");
                }
            }
        };
    }

    private static boolean usesMainThreadLooper(@NotNull UClass handlerClass) {
        List<UMethod> constructors = handlerClass.getUConstructors();
        if (constructors.isEmpty()) {
            return true;
        }

        boolean hasMainThreadConstructor = false;
        for (UMethod constructor : constructors) {
            if (constructorUsesMainThreadLooper(constructor, handlerClass, new HashSet<UMethod>())) {
                hasMainThreadConstructor = true;
            }
        }

        return hasMainThreadConstructor;
    }

    private static boolean constructorUsesMainThreadLooper(
            @NotNull UMethod constructor,
            @NotNull UClass handlerClass,
            @NotNull Set<UMethod> visited) {
        if (!visited.add(constructor)) {
            return false;
        }

        final List<UCallExpression> superCalls = new ArrayList<UCallExpression>();
        final List<UCallExpression> thisCalls = new ArrayList<UCallExpression>();

        UExpression body = constructor.getUastBody();
        if (body != null) {
            body.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitCallExpression(@NotNull UCallExpression node) {
                    if (node.getKind() != UastCallKind.FUNCTION_CALL) {
                        return super.visitCallExpression(node);
                    }

                    PsiMethod resolved = node.resolve();
                    if (resolved == null || !resolved.isConstructor()) {
                        return super.visitCallExpression(node);
                    }

                    PsiClass containingClass = resolved.getContainingClass();
                    if (containingClass == null) {
                        return super.visitCallExpression(node);
                    }

                    String qualifiedName = containingClass.getQualifiedName();
                    if (ANDROID_OS_HANDLER.equals(qualifiedName)) {
                        superCalls.add(node);
                    } else if (qualifiedName != null
                            && qualifiedName.equals(handlerClass.getPsi().getQualifiedName())) {
                        thisCalls.add(node);
                    }

                    return super.visitCallExpression(node);
                }
            });
        }

        if (!superCalls.isEmpty()) {
            return handlerConstructorUsesMainLooper(superCalls.get(0));
        }

        if (!thisCalls.isEmpty()) {
            PsiMethod target = thisCalls.get(0).resolve();
            if (target != null) {
                for (UMethod candidate : handlerClass.getUConstructors()) {
                    if (candidate.getPsi() == target) {
                        return constructorUsesMainThreadLooper(candidate, handlerClass, visited);
                    }
                }
            }
        }

        // Implicit super() uses the current thread's Looper (typically the main Looper).
        return true;
    }

    private static boolean handlerConstructorUsesMainLooper(@NotNull UCallExpression call) {
        PsiMethod method = call.resolve();
        if (method == null) {
            return true;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        int count = parameters.length;
        if (count == 0) {
            return true;
        }

        PsiType firstType = parameters[0].getType();
        String firstCanonical = firstType.getCanonicalText();
        if (ANDROID_OS_LOOPER.equals(firstCanonical)) {
            List<UExpression> arguments = call.getValueArguments();
            if (!arguments.isEmpty()) {
                return isMainLooperCall(arguments.get(0));
            }
            return true;
        }

        // Handler(Callback) and other constructors without a Looper use the current Looper.
        return true;
    }

    private static boolean isMainLooperCall(@NotNull UExpression expression) {
        if (!(expression instanceof UCallExpression)) {
            return false;
        }
        UCallExpression call = (UCallExpression) expression;
        PsiMethod method = call.resolve();
        if (method == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null
                && ANDROID_OS_LOOPER.equals(containingClass.getQualifiedName())
                && "getMainLooper".equals(method.getName());
    }
}