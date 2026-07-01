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
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";
    private static final String ANDROID_OS_MESSAGE_QUEUE = "android.os.MessageQueue";

    public static final Issue ISSUE =
            Issue.create(
                            "HandlerLeak",
                            "Handler reference leaks",
                            "Since this Handler is declared as an inner class, it may prevent the "
                                    + "outer class from being garbage collected. If the Handler is "
                                    + "using a Looper or MessageQueue for a thread other than the "
                                    + "main thread, then there is no issue. If the Handler is "
                                    + "using the Looper or MessageQueue of the main thread, you "
                                    + "need to fix your Handler declaration, as follows: declare "
                                    + "the Handler as a static class; in the outer class, "
                                    + "instantiate a WeakReference to the outer class and pass "
                                    + "this object to your Handler when you instantiate the "
                                    + "Handler; make all references to members of the outer class "
                                    + "using the WeakReference object.",
                            Category.PERFORMANCE,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public HandlerDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_OS_HANDLER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (ANDROID_OS_HANDLER.equals(declaration.getQualifiedName())) {
            return;
        }

        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }

        if (psiClass.getContainingClass() == null) {
            return;
        }

        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        for (PsiMethod method : psiClass.getConstructors()) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            for (PsiParameter parameter : parameters) {
                PsiType type = parameter.getType();
                if (type == null) {
                    continue;
                }
                String canonicalText = type.getCanonicalText();
                if (ANDROID_OS_LOOPER.equals(canonicalText)
                        || ANDROID_OS_MESSAGE_QUEUE.equals(canonicalText)) {
                    return;
                }
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This Handler class should be static or leaks might occur");
    }
}