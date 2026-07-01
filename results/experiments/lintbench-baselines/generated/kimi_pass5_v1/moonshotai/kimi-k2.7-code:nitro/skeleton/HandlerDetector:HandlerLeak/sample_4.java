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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USuperExpression;
import org.jetbrains.uast.UThisExpression;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a Looper or MessageQueue for a thread other than the main thread, there is no issue. If the Handler is using the Looper or MessageQueue of the main thread, declare the Handler as a static class, use a WeakReference to the outer class, and refer to outer class members through the WeakReference.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Handler");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null || psiClass.isInterface() || psiClass.isEnum()) {
            return;
        }

        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        if (psiClass.getContainingClass() == null) {
            return;
        }

        List<UMethod> constructors = new ArrayList<>();
        for (UMethod method : declaration.getMethods()) {
            PsiMethod psiMethod = method.getJavaPsi();
            if (psiMethod != null && psiMethod.isConstructor()) {
                constructors.add(method);
            }
        }

        if (constructors.isEmpty()) {
            report(context, declaration);
            return;
        }

        Set<PsiMethod> visited = new HashSet<>();
        for (UMethod constructor : constructors) {
            if (constructorUsesMainThreadLooper(constructor, declaration, context, visited)) {
                report(context, declaration);
                return;
            }
        }
    }

    private boolean constructorUsesMainThreadLooper(UMethod constructor, UClass currentClass,
            JavaContext context, Set<PsiMethod> visited) {
        PsiMethod psiConstructor = constructor.getJavaPsi();
        if (psiConstructor == null || !visited.add(psiConstructor)) {
            return true;
        }

        UExpression body = constructor.getUastBody();
        UCallExpression invocation = findConstructorInvocation(body, context, currentClass);
        if (invocation == null) {
            return true;
        }

        UExpression receiver = invocation.getReceiver();
        if (receiver instanceof UThisExpression) {
            PsiMethod target = context.getEvaluator().resolve(invocation);
            if (target == null) {
                return true;
            }
            for (UMethod method : currentClass.getMethods()) {
                if (method.getJavaPsi() == target) {
                    return constructorUsesMainThreadLooper(method, currentClass, context, visited);
                }
            }
            return true;
        }

        if (receiver instanceof USuperExpression) {
            return isMainThreadHandlerConstructor(invocation, context);
        }

        return true;
    }

    private UCallExpression findConstructorInvocation(UExpression body, JavaContext context,
            UClass currentClass) {
        if (body instanceof UBlockExpression) {
            for (UExpression expression : ((UBlockExpression) body).getExpressions()) {
                UCallExpression call = findTopLevelConstructorCall(expression, context, currentClass);
                if (call != null) {
                    return call;
                }
            }
        }
        return null;
    }

    private UCallExpression findTopLevelConstructorCall(UExpression expression,
            JavaContext context, UClass currentClass) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            UExpression receiver = call.getReceiver();
            if (receiver instanceof UThisExpression || receiver instanceof USuperExpression) {
                PsiMethod method = context.getEvaluator().resolve(call);
                if (method != null && method.isConstructor()) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null) {
                        if (receiver instanceof UThisExpression
                                && containingClass == currentClass.getJavaPsi()) {
                            return call;
                        }
                        if (receiver instanceof USuperExpression
                                && "android.os.Handler".equals(containingClass.getQualifiedName())) {
                            return call;
                        }
                    }
                }
            }
            return null;
        }

        for (UElement child : expression.getChildren()) {
            if (child instanceof UExpression) {
                UCallExpression found = findTopLevelConstructorCall((UExpression) child,
                        context, currentClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isMainThreadHandlerConstructor(UCallExpression superCall, JavaContext context) {
        List<UExpression> args = superCall.getValueArguments();
        if (args.isEmpty()) {
            return true;
        }

        UExpression firstArg = args.get(0);
        PsiType type = firstArg.getExpressionType();
        if (type != null && "android.os.Looper".equals(type.getCanonicalText())) {
            return isMainLooper(firstArg, context);
        }

        return true;
    }

    private boolean isMainLooper(UExpression expression, JavaContext context) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            if ("getMainLooper".equals(call.getMethodName())) {
                PsiMethod method = context.getEvaluator().resolve(call);
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null
                            && "android.os.Looper".equals(containingClass.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void report(JavaContext context, UClass declaration) {
        String message = "This Handler class should be static or leaks might occur";
        context.report(ISSUE, declaration, context.getLocation(declaration), message);
    }
}