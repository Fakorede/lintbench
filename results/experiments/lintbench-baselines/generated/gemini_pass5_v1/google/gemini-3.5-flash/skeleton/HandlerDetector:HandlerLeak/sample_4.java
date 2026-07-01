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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the "
                            + "outer class from being garbage collected. If the Handler is "
                            + "using a Looper or MessageQueue for a thread other than the "
                            + "main thread, then there is no issue. If the Handler is using "
                            + "the Looper or MessageQueue of the main thread, you need to "
                            + "fix your Handler declaration, as follows: Declare the "
                            + "Handler as a static class; In the outer class, instantiate a "
                            + "WeakReference to the outer class and pass this object to your "
                            + "Handler when you instantiate the Handler; Make all "
                            + "references to members of the outer class using the "
                            + "WeakReference object.",
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
        PsiClass psiClass = declaration;

        // Check if it's an inner class (nested and not static)
        PsiClass containingClass = psiClass.getContainingClass();
        if (containingClass == null) {
            return;
        }

        // If it is static, it won't leak the outer class
        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        // If it's inside an anonymous class or is anonymous itself, we must check if it has a static context
        if (psiClass instanceof PsiAnonymousClass) {
            PsiElement parent = psiClass.getParent();
            while (parent != null && !(parent instanceof PsiClass)) {
                if (parent instanceof PsiMethod) {
                    if (((PsiMethod) parent).hasModifierProperty(PsiModifier.STATIC)) {
                        return;
                    }
                } else if (parent instanceof PsiField) {
                    if (((PsiField) parent).hasModifierProperty(PsiModifier.STATIC)) {
                        return;
                    }
                }
                parent = parent.getParent();
            }
        }

        // Report the issue
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This Handler class should be static or leaks might occur");
    }
}