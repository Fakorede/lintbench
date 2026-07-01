package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements SourceCodeScanner {
    public static final Issue ISSUE = Issue.create(
        "HandlerLeak",
        "Handler reference leaks",
        "Since this Handler is declared as an inner class, it may prevent the " +
        "outer class from being garbage collected. If the Handler is using a " +
        "`Looper` or `MessageQueue` for a thread other than the main thread, then " +
        "there is no issue. If the `Handler` is using the `Looper` or `MessageQueue` " +
        "of the main thread, you need to fix your `Handler` declaration, as follows: " +
        "Declare the `Handler` as a static class; In the outer class, instantiate a " +
        "`WeakReference` to the outer class and pass this object to your `Handler` " +
        "when you instantiate the `Handler`; Make all references to members of the " +
        "outer class using the `WeakReference` object.",
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
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                String name = node.getName();
                if (name == null) {
                    return;
                }

                UClass outerClass = node.getContainingUClass();
                if (outerClass == null) {
                    return;
                }

                if (node.hasModifier(PsiModifier.STATIC)) {
                    return;
                }

                if (context.getEvaluator().extendsClass(node, "android.os.Handler", false)) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                        "This Handler class should be static or leaks might occur (" + name + ")");
                }
            }
        };
    }
}