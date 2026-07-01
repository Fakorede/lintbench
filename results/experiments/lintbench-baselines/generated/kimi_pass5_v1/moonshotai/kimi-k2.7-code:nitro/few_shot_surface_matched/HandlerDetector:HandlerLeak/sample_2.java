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
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_HANDLER = "android.os.Handler";
    private static final String ANDROID_LOOPER = "android.os.Looper";

    public static final Issue HANDLER_LEAK =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the "
                            + "outer class from being garbage collected. If the Handler is using "
                            + "a `Looper` or `MessageQueue` for a thread other than the main "
                            + "thread, then there is no issue. If the `Handler` is using the "
                            + "`Looper` or `MessageQueue` of the main thread, you need to fix "
                            + "your `Handler` declaration, as follows: Declare the `Handler` as "
                            + "a static class; In the outer class, instantiate a `WeakReference` "
                            + "to the outer class and pass this object to your `Handler` when you "
                            + "instantiate the `Handler`; Make all references to members of the "
                            + "outer class using the `WeakReference` object.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
            .setAndroidSpecific(true);

    public HandlerDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_HANDLER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (ANDROID_HANDLER.equals(declaration.getQualifiedName())) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        if (psiClass.getParent() instanceof PsiJavaFile) {
            return;
        }

        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        if (hasOnlyLooperConstructors(psiClass)) {
            return;
        }

        context.report(
                HANDLER_LEAK,
                declaration,
                context.getNameLocation(declaration),
                "This Handler class should be static or leaks might occur");
    }

    private static boolean hasOnlyLooperConstructors(PsiClass psiClass) {
        PsiMethod[] constructors = psiClass.getConstructors();
        if (constructors.length == 0) {
            return false;
        }

        for (PsiMethod constructor : constructors) {
            boolean foundLooper = false;
            for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                if (ANDROID_LOOPER.equals(parameter.getType().getCanonicalText())) {
                    foundLooper = true;
                    break;
                }
            }
            if (!foundLooper) {
                return false;
            }
        }

        return true;
    }
}