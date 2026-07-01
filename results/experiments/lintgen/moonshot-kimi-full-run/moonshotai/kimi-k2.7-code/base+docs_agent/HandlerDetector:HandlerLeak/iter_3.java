package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USuperExpression;
import org.jetbrains.uast.UThisExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class HandlerDetector extends Detector implements Detector.UastScanner {
    private static final String HANDLER_CLS = "android.os.Handler";
    private static final String LOOPER_CLS = "android.os.Looper";

    private static final String EXPLANATION =
            "Since this Handler is declared as an inner class, it may prevent the outer class "
                    + "from being garbage collected. If the Handler is using a `Looper` or "
                    + "`MessageQueue` for a thread other than the main thread, then there is no "
                    + "issue. If the `Handler` is using the `Looper` or `MessageQueue` of the "
                    + "main thread, you need to fix your `Handler` declaration, as follows: "
                    + "Declare the `Handler` as a static class; In the outer class, instantiate "
                    + "a `WeakReference` to the outer class and pass this object to your "
                    + "`Handler` when you instantiate the `Handler`; Make all references to "
                    + "members of the outer class using the `WeakReference` object.";

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            EXPLANATION,
            Category.PERFORMANCE,
            4,
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
                if (isLeakyHandler(context, node)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "This Handler class should be static or leaks might occur");
                }
            }
        };
    }

    private boolean isLeakyHandler(@NotNull JavaContext context, @NotNull UClass node) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(node, HANDLER_CLS, false)) {
            return false;
        }
        if (node.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (node.getContainingClass() == null && !(node instanceof PsiAnonymousClass)) {
            return false;
        }
        return usesMainThreadLooper(node);
    }

    private boolean usesMainThreadLooper(@NotNull UClass node) {
        if (node instanceof PsiAnonymousClass) {
            UCallExpression enclosing = findEnclosingCall(node);
            if (enclosing != null) {
                return isMainThreadSuperCall(enclosing);
            }
        }

        PsiMethod[] constructors = node.getConstructors();
        if (constructors.length == 0) {
            return true;
        }

        for (PsiMethod constructor : constructors) {
            UMethod uMethod = toUMethod(constructor);
            if (uMethod == null) {
                return true;
            }
            if (isMainThreadConstructor(uMethod, new HashSet<UMethod>())) {
                return true;
            }
        }
        return false;
    }

    private UMethod toUMethod(@NotNull PsiMethod method) {
        if (method instanceof UMethod) {
            return (UMethod) method;
        }
        return UastContextKt.toUElement(method, UMethod.class);
    }

    private boolean isMainThreadConstructor(
            @NotNull UMethod constructor,
            @NotNull Set<UMethod> visited) {
        if (!visited.add(constructor)) {
            return true;
        }

        UExpression body = constructor.getUastBody();
        if (body == null) {
            return true;
        }

        UCallExpression call = findExplicitConstructorCall(body);
        if (call == null) {
            return true;
        }

        UExpression receiver = call.getReceiver();
        if (receiver instanceof UThisExpression
                || "this".equals(call.getMethodName())) {
            PsiMethod target = call.resolve();
            if (target == null) {
                return true;
            }
            UMethod targetMethod = toUMethod(target);
            if (targetMethod == null) {
                return true;
            }
            return isMainThreadConstructor(targetMethod, visited);
        }

        return isMainThreadSuperCall(call);
    }

    private boolean isMainThreadSuperCall(@NotNull UCallExpression call) {
        int looperIndex = findLooperParameterIndex(call);
        if (looperIndex < 0) {
            return true;
        }

        List<UExpression> args = call.getValueArguments();
        if (looperIndex >= args.size()) {
            return true;
        }

        return isMainLooperExpression(args.get(looperIndex));
    }

    private UCallExpression findExplicitConstructorCall(@NotNull UExpression body) {
        ConstructorCallFinder finder = new ConstructorCallFinder();
        body.accept(finder);
        return finder.call;
    }

    private int findLooperParameterIndex(@NotNull UCallExpression call) {
        PsiMethod resolved = call.resolve();
        if (resolved != null) {
            PsiParameterList list = resolved.getParameterList();
            PsiParameter[] parameters = list.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                PsiType type = parameters[i].getType();
                if (type != null && LOOPER_CLS.equals(type.getCanonicalText())) {
                    return i;
                }
            }
        } else {
            List<UExpression> args = call.getValueArguments();
            for (int i = 0; i < args.size(); i++) {
                PsiType type = args.get(i).getExpressionType();
                if (type != null && LOOPER_CLS.equals(type.getCanonicalText())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean isMainLooperExpression(UExpression expression) {
        if (!(expression instanceof UCallExpression)) {
            return false;
        }
        UCallExpression call = (UCallExpression) expression;
        if (!"getMainLooper".equals(call.getMethodName())) {
            return false;
        }

        PsiMethod method = call.resolve();
        if (method == null) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType != null && LOOPER_CLS.equals(returnType.getCanonicalText())) {
            return true;
        }

        PsiClass containingClass = method.getContainingClass();
        return containingClass != null
                && LOOPER_CLS.equals(containingClass.getQualifiedName());
    }

    private UCallExpression findEnclosingCall(@NotNull UClass node) {
        UElement current = node.getUastParent();
        while (current != null) {
            if (current instanceof UCallExpression) {
                return (UCallExpression) current;
            }
            if (current instanceof UClass) {
                return null;
            }
            current = current.getUastParent();
        }
        return null;
    }

    private static class ConstructorCallFinder extends AbstractUastVisitor {
        UCallExpression call;

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true;
        }

        @Override
        public boolean visitCallExpression(@NotNull UCallExpression node) {
            if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                UExpression receiver = node.getReceiver();
                if (receiver instanceof UThisExpression
                        || receiver instanceof USuperExpression) {
                    call = node;
                    return true;
                }
            }
            return super.visitCallExpression(node);
        }
    }
}