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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

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
    public void visitClass(JavaContext context, UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        PsiClass containingClass = psiClass.getContainingClass();
        if (containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            if (!isInStaticContext(psiClass)) {
                Location location = declaration.getName() != null
                    ? context.getNameLocation(declaration)
                    : context.getLocation(declaration);
                context.report(
                    ISSUE,
                    declaration,
                    location,
                    "This Handler class should be static or leaks might occur"
                );
            }
        }
    }

    private boolean isInStaticContext(PsiClass psiClass) {
        PsiElement parent = psiClass.getParent();
        while (parent != null) {
            if (parent instanceof PsiMethod) {
                if (((PsiMethod) parent).hasModifierProperty(PsiModifier.STATIC)) {
                    return true;
                }
            }
            if (parent instanceof PsiClass) {
                break;
            }
            parent = parent.getParent();
        }
        return false;
    }
}