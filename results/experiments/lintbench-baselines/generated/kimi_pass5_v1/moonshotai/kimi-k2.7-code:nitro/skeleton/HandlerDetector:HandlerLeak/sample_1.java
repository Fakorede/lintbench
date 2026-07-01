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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";
    private static final String ANDROID_OS_MESSAGE_QUEUE = "android.os.MessageQueue";

    private static final String MESSAGE =
            "This Handler should be declared as a static class or should use a WeakReference to the outer class to avoid a leak";

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a Looper or MessageQueue for a thread other than the main thread, then there is no issue. If the Handler is using the Looper or MessageQueue of the main thread, you need to fix your Handler declaration, as follows: Declare the Handler as a static class; In the outer class, instantiate a WeakReference to the outer class and pass this object to your Handler when you instantiate the Handler; Make all references to members of the outer class using the WeakReference object.",
                    Category.PERFORMANCE,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_OS_HANDLER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        // Only non-static inner classes hold a reference to the outer class.
        if (declaration.getContainingClass() == null) {
            return;
        }

        PsiModifierList modifierList = declaration.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        // If every constructor accepts an explicit Looper or MessageQueue, it may be
        // associated with a non-main thread and is not necessarily leaking.
        if (allConstructorsUseLooperOrMessageQueue(declaration)) {
            return;
        }

        context.report(ISSUE, declaration, context.getLocation(declaration), MESSAGE);
    }

    private static boolean allConstructorsUseLooperOrMessageQueue(UClass declaration) {
        boolean foundConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor()) {
                foundConstructor = true;
                if (!hasLooperOrMessageQueueParameter(method)) {
                    return false;
                }
            }
        }
        return foundConstructor;
    }

    private static boolean hasLooperOrMessageQueueParameter(UMethod constructor) {
        for (UParameter parameter : constructor.getUastParameters()) {
            PsiParameter psiParameter = parameter.getJavaPsi();
            if (psiParameter != null) {
                PsiType type = psiParameter.getType();
                if (type != null) {
                    String canonical = type.getCanonicalText();
                    if (ANDROID_OS_LOOPER.equals(canonical)
                            || ANDROID_OS_MESSAGE_QUEUE.equals(canonical)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}