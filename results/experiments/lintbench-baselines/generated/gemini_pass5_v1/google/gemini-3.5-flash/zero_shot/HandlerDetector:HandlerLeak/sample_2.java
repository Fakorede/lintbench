package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;

public class HandlerDetector extends Detector implements Detector.UastScanner {

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

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().inheritsFrom(node, "android.os.Handler", false)) {
                    boolean isStatic = node.hasModifierProperty(PsiModifier.STATIC);
                    PsiClass containingClass = node.getContainingClass();

                    if (containingClass != null && !isStatic) {
                        if (!isInStaticContext(node)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getNameLocation(node),
                                    "This `Handler` class should be static or leaks might occur"
                            );
                        }
                    }
                }
            }
        };
    }

    private boolean isInStaticContext(UClass node) {
        UElement current = node.getUastParent();
        while (current != null) {
            if (current instanceof UMethod) {
                if (((UMethod) current).hasModifierProperty(PsiModifier.STATIC)) {
                    return true;
                }
            } else if (current instanceof UClass) {
                break;
            }
            current = current.getUastParent();
        }
        return false;
    }
}