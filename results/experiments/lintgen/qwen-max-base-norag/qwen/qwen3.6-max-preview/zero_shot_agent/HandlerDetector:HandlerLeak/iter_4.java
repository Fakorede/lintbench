package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this Handler is declared as an inner class, it may prevent the " +
            "outer class from being garbage collected. If the Handler is using a " +
            "`Looper` or `MessageQueue` for a thread other than the main thread, " +
            "then there is no issue. If the `Handler` is using the `Looper` or " +
            "`MessageQueue` of the main thread, you need to fix your `Handler` " +
            "declaration, as follows: Declare the `Handler` as a static class; " +
            "In the outer class, instantiate a `WeakReference` to the outer class " +
            "and pass this object to your `Handler` when you instantiate the " +
            "`Handler`; Make all references to members of the outer class using " +
            "the `WeakReference` object.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        if (!context.getEvaluator().extendsClass(node, "android.os.Handler", false)) {
            return;
        }

        UClass outer = node.getContainingUClass();
        if (outer == null) {
            return;
        }

        if (node.getModifierList() != null && node.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        String name = node.getName();
        String message = name != null
                ? "This Handler class should be static or leaks might occur (" + name + ")"
                : "This Handler class should be static or leaks might occur";

        context.report(ISSUE, context.getNameLocation(node), message);
    }
}