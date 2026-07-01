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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String MESSAGE = "This Handler class should be static or leaks might occur";

    private static final String EXPLANATION =
            "Since this Handler is declared as an inner class, it may prevent the outer class "
                    + "from being garbage collected. If the Handler is using a `Looper` or "
                    + "`MessageQueue` for a thread other than the main thread, then there is no "
                    + "issue. If the `Handler` is using the `Looper` or `MessageQueue` of the main "
                    + "thread, you need to fix your `Handler` declaration, as follows: Declare the "
                    + "`Handler` as a static class; In the outer class, instantiate a `WeakReference` "
                    + "to the outer class and pass this object to your `Handler` when you instantiate "
                    + "the Handler; Make all references to members of the outer class using the "
                    + "`WeakReference` object.";

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    EXPLANATION,
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Handler");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        if (declaration.getContainingClass() == null) {
            return;
        }

        if (declaration instanceof UAnonymousClass) {
            UCallExpression call = UastUtils.getParentOfType(declaration, UCallExpression.class);
            if (call != null) {
                for (UExpression argument : call.getValueArguments()) {
                    if (isLooperType(argument.getExpressionType())) {
                        return;
                    }
                }
            }
        } else {
            for (PsiMethod constructor : declaration.getConstructors()) {
                for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                    if (isLooperType(parameter.getType())) {
                        return;
                    }
                }
            }
        }

        Location location = context.getNameLocation(declaration);
        context.report(ISSUE, declaration, location, MESSAGE);
    }

    private static boolean isLooperType(PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();
            if (resolved != null && "android.os.Looper".equals(resolved.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}