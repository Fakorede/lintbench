package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
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
    private static final String LOOPER_CLASS = "android.os.Looper";
    private static final String GET_MAIN_LOOPER = "getMainLooper";

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a Looper or MessageQueue for a thread other than the main thread, then there is no issue. If the Handler is using the Looper or MessageQueue of the main thread, you need to fix your Handler declaration, as follows: Declare the Handler as a static class; In the outer class, instantiate a WeakReference to the outer class and pass this object to your Handler when you instantiate the Handler; Make all references to members of the outer class using the WeakReference object.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    @NonNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HANDLER_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        if (!isInnerOrAnonymous(declaration)) {
            return;
        }

        if (isValidWorkerThreadHandler(context, declaration)) {
            return;
        }

        String message =
                "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a Looper or MessageQueue for a thread other than the main thread, then there is no issue. If the Handler is using the Looper or MessageQueue of the main thread, you need to fix your Handler declaration, as follows: Declare the Handler as a static class; In the outer class, instantiate a WeakReference to the outer class and pass this object to your Handler when you instantiate the Handler; Make all references to members of the outer class using the WeakReference object.";

        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    private static boolean isInnerOrAnonymous(@NonNull UClass declaration) {
        return declaration instanceof UAnonymousClass
                || declaration.getJavaPsi().getContainingClass() != null;
    }

    private static boolean isValidWorkerThreadHandler(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            UCallExpression parentCall = findParentCall(declaration);
            if (parentCall != null) {
                Boolean usesMain = isMainLooperConstructorCall(context, parentCall);
                if (usesMain != null) {
                    return !usesMain;
                }
            }

            PsiClass psiClass = declaration.getJavaPsi();
            if (psiClass instanceof PsiAnonymousClass) {
                PsiExpressionList list = ((PsiAnonymousClass) psiClass).getArgumentList();
                if (list != null) {
                    Boolean usesMain =
                            isMainLooperConstructorCall(context, list.getExpressions());
                    if (usesMain != null) {
                        return !usesMain;
                    }
                }
            }

            return false;
        }

        for (UMethod method : declaration.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }

            UExpression body = method.getUastBody();
            if (body == null) {
                continue;
            }

            UCallExpression superCall = findSuperCall(body);
            if (superCall == null) {
                continue;
            }

            Boolean usesMain = isMainLooperConstructorCall(context, superCall);
            if (usesMain != null) {
                return !usesMain;
            }
        }

        return false;
    }

    private static UCallExpression findSuperCall(@NonNull UExpression expression) {
        if (expression instanceof UCallExpression
                && ((UCallExpression) expression).isSuperCall()) {
            return (UCallExpression) expression;
        }

        if (expression instanceof UBlockExpression) {
            for (UExpression child : ((UBlockExpression) expression).getExpressions()) {
                UCallExpression result = findSuperCall(child);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private static UCallExpression findParentCall(@NonNull UClass declaration) {
        UElement parent = declaration.getUastParent();
        int maxDepth = 5;
        while (parent != null && maxDepth-- > 0) {
            if (parent instanceof UCallExpression) {
                return (UCallExpression) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    private static Boolean isMainLooperConstructorCall(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        boolean hasLooperArgument = false;
        JavaEvaluator evaluator = context.getEvaluator();

        for (UExpression argument : call.getValueArguments()) {
            PsiType type = evaluator.getType(argument);
            if (type != null && LOOPER_CLASS.equals(type.getCanonicalText())) {
                hasLooperArgument = true;
                if (isMainLooperReference(argument)) {
                    return Boolean.TRUE;
                }
            }
        }

        return hasLooperArgument ? Boolean.FALSE : null;
    }

    private static Boolean isMainLooperConstructorCall(
            @NonNull JavaContext context, @NonNull PsiExpression[] arguments) {
        boolean hasLooperArgument = false;

        for (PsiExpression argument : arguments) {
            PsiType type = argument.getType();
            if (type != null && LOOPER_CLASS.equals(type.getCanonicalText())) {
                hasLooperArgument = true;
                if (isMainLooperReference(argument)) {
                    return Boolean.TRUE;
                }
            }
        }

        return hasLooperArgument ? Boolean.FALSE : null;
    }

    private static boolean isMainLooperReference(@NonNull UExpression expression) {
        if (!(expression instanceof UCallExpression)) {
            return false;
        }

        PsiMethod method = ((UCallExpression) expression).resolve();
        if (method == null) {
            return false;
        }

        PsiClass containing = method.getContainingClass();
        return GET_MAIN_LOOPER.equals(method.getName())
                && containing != null
                && LOOPER_CLASS.equals(containing.getQualifiedName());
    }

    private static boolean isMainLooperReference(@NonNull PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }

        PsiMethod method = ((PsiMethodCallExpression) expression).resolveMethod();
        if (method == null) {
            return false;
        }

        PsiClass containing = method.getContainingClass();
        return GET_MAIN_LOOPER.equals(method.getName())
                && containing != null
                && LOOPER_CLASS.equals(containing.getQualifiedName());
    }
}