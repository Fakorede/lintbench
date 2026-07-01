package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.JvmModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementHandler;

import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements SourceCodeScanner {

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
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.getContainingClass() == null) {
                    return;
                }
                if (node.getModifiers().contains(JvmModifier.STATIC)) {
                    return;
                }
                if (context.getEvaluator().extendsClass(node, "android.os.Handler", false)) {
                    context.report(ISSUE, node, context.getLocation(node),
                        "This Handler class should be static or leaks might occur");
                }
            }
        };
    }
}