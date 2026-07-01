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
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UClassInitializer;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UastCallKind;

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
    public void visitClass(@NotNull JavaContext context, @NotNull UClass uClass) {
        if (isInStaticContext(uClass)) {
            return;
        }

        PsiClass outerClass = uClass.getContainingClass();
        if (outerClass == null || outerClass.isInterface()) {
            return;
        }

        if (usesNonMainLooper(uClass)) {
            return;
        }

        context.report(
                ISSUE,
                uClass,
                context.getNameLocation(uClass),
                "This `Handler` class should be static or leaks might occur"
        );
    }

    private boolean isInStaticContext(@NotNull UClass uClass) {
        if (uClass.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        UElement current = uClass.getUastParent();
        while (current != null) {
            if (current instanceof UMethod) {
                UMethod method = (UMethod) current;
                if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    return true;
                }
            } else if (current instanceof UClassInitializer) {
                UClassInitializer initializer = (UClassInitializer) current;
                if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                    return true;
                }
            } else if (current instanceof UClass) {
                break;
            }
            current = current.getUastParent();
        }
        return false;
    }

    private boolean usesNonMainLooper(@NotNull UClass uClass) {
        if (uClass instanceof UAnonymousClass) {
            UElement parent = uClass.getUastParent();
            if (parent instanceof UObjectLiteralExpression) {
                UObjectLiteralExpression literal = (UObjectLiteralExpression) parent;
                return hasNonMainLooper(literal);
            }
        } else {
            for (UMethod method : uClass.getMethods()) {
                if (method.isConstructor()) {
                    if (passesLooperToSuper(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean passesLooperToSuper(@NotNull UMethod constructor) {
        UExpression body = constructor.getUastBody();
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (!expressions.isEmpty()) {
                UExpression first = expressions.get(0);
                if (first instanceof UCallExpression) {
                    UCallExpression call = (UCallExpression) first;
                    if (call.getKind() == UastCallKind.CONSTRUCTOR_CALL || "super".equals(call.getMethodName())) {
                        for (UExpression arg : call.getValueArguments()) {
                            PsiType type = arg.getExpressionType();
                            if (type != null && type.getCanonicalText().equals("android.os.Looper")) {
                                if (!isMainLooper(arg)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasNonMainLooper(@NotNull UCallExpression call) {
        for (UExpression arg : call.getValueArguments()) {
            PsiType type = arg.getExpressionType();
            if (type != null && type.getCanonicalText().equals("android.os.Looper")) {
                return !isMainLooper(arg);
            }
        }
        return false;
    }

    private boolean isMainLooper(@NotNull UExpression arg) {
        if (arg instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) arg;
            PsiMethod method = call.resolve();
            if (method != null) {
                if ("getMainLooper".equals(method.getName())) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null && "android.os.Looper".equals(containingClass.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}