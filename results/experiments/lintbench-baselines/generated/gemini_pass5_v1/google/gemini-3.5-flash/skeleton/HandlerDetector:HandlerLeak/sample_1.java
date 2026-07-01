package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the "
                            + "outer class from being garbage collected. If the Handler is "
                            + "using a `Looper` or `MessageQueue` for a thread other than the "
                            + "main thread, then there is no issue. If the `Handler` is using "
                            + "the `Looper` or `MessageQueue` of the main thread, you need to "
                            + "fix your `Handler` declaration, as follows: Declare the "
                            + "`Handler` as a static class; In the outer class, instantiate a "
                            + "`WeakReference` to the outer class and pass this object to your "
                            + "`Handler` when you instantiate the `Handler`; Make all "
                            + "references to members of the outer class using the "
                            + "`WeakReference` object.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Handler");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass == null && !(declaration instanceof UAnonymousClass)) {
            return;
        }

        // Check if it's an anonymous class with a non-main looper
        if (declaration instanceof UAnonymousClass) {
            UAnonymousClass anonymous = (UAnonymousClass) declaration;
            UCallExpression call = anonymous.getConstructorCall();
            if (call != null) {
                List<UExpression> args = call.getValueArguments();
                for (UExpression arg : args) {
                    PsiType type = arg.getExpressionType();
                    if (type != null && "android.os.Looper".equals(type.getCanonicalText())) {
                        if (!isMainLooperExpression(arg)) {
                            return;
                        }
                    }
                }
            }
        } else {
            // Check if it has a constructor passing a non-main Looper
            if (hasNonMainLooperConstructor(declaration)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This `Handler` class should be static or leaks might occur");
    }

    private boolean hasNonMainLooperConstructor(UClass declaration) {
        UMethod[] constructors = declaration.getMethods();
        for (UMethod method : constructors) {
            if (method.isConstructor()) {
                if (callsSuperWithNonMainLooper(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean callsSuperWithNonMainLooper(UMethod constructor) {
        UExpression body = constructor.getUastBody();
        if (body == null) {
            return false;
        }
        final boolean[] foundNonMain = {false};
        body.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitCallExpression(@NonNull UCallExpression node) {
                        PsiMethod resolved = node.resolve();
                        if (resolved != null
                                && resolved.isConstructor()
                                && "android.os.Handler".equals(
                                        resolved.getContainingClass() != null
                                                ? resolved.getContainingClass().getQualifiedName()
                                                : null)) {
                            List<UExpression> args = node.getValueArguments();
                            if (!args.isEmpty()) {
                                for (UExpression arg : args) {
                                    PsiType type = arg.getExpressionType();
                                    if (type != null
                                            && "android.os.Looper".equals(
                                                    type.getCanonicalText())) {
                                        if (!isMainLooperExpression(arg)) {
                                            foundNonMain[0] = true;
                                        }
                                    }
                                }
                            }
                        }
                        return super.visitCallExpression(node);
                    }
                });
        return foundNonMain[0];
    }

    private boolean isMainLooperExpression(UExpression expression) {
        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod resolved = call.resolve();
            if (resolved != null) {
                if ("getMainLooper".equals(resolved.getName())) {
                    PsiClass containingClass = resolved.getContainingClass();
                    if (containingClass != null
                            && "android.os.Looper".equals(containingClass.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}