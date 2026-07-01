package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final String MESSAGE = "Handler reference leaks";

    private static final String EXPLANATION =
            "Since this Handler is declared as an inner class, it may prevent the outer class "
                    + "from being garbage collected. If the Handler is using a `Looper` or "
                    + "`MessageQueue` for a thread other than the main thread, then there is no "
                    + "issue. If the Handler is using the `Looper` or `MessageQueue` of the main "
                    + "thread, you need to fix your Handler declaration, as follows: Declare the "
                    + "Handler as a static class; In the outer class, instantiate a `WeakReference` "
                    + "to the outer class and pass this object to your Handler when you instantiate "
                    + "the Handler; Make all references to members of the outer class using the "
                    + "`WeakReference` object.";

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            EXPLANATION,
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }

                if (!extendsHandler(node)) {
                    return;
                }

                if (node.getContainingClass() == null) {
                    return;
                }

                if (hasLooperParameter(node)) {
                    return;
                }

                Location location = context.getNameLocation(node);
                context.report(ISSUE, node, location, MESSAGE);
            }
        };
    }

    private static boolean extendsHandler(@NotNull UClass node) {
        PsiClass superClass = node.getSuperClass();
        return superClass != null
                && "android.os.Handler".equals(superClass.getQualifiedName());
    }

    private static boolean hasLooperParameter(@NotNull UClass node) {
        for (PsiMethod constructor : node.getConstructors()) {
            for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                PsiType type = parameter.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null
                            && "android.os.Looper".equals(resolved.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}