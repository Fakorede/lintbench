package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
        "MissingOnPlayFromSearch",
        "Missing `onPlayFromSearch`",
        "To support voice searches on Android Auto, in addition to adding an " +
        "`intent-filter` for the action `onPlayFromSearch`, you also need to " +
        "override and implement `onPlayFromSearch(String query, Bundle bundle)`.\n\n" +
        "Reference: https://developer.android.com/training/auto/audio/index.html#support_voice",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        if (node.isInterface()) {
            return;
        }

        PsiClass psiClass = node.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        if (psiClass.getModifierList() != null && psiClass.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        boolean isCallback = evaluator.extendsClass(psiClass, "android.media.session.MediaSession.Callback", false)
                || evaluator.extendsClass(psiClass, "android.support.v4.media.session.MediaSessionCompat.Callback", false)
                || evaluator.extendsClass(psiClass, "androidx.media.session.MediaSessionCompat.Callback", false);

        if (!isCallback) {
            return;
        }

        boolean hasOnPlayFromSearch = false;
        for (UMethod method : node.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    PsiType type1 = parameters.get(0).getType();
                    PsiType type2 = parameters.get(1).getType();
                    if (type1 != null && type2 != null) {
                        String t1 = type1.getCanonicalText();
                        String t2 = type2.getCanonicalText();
                        if (t1.endsWith("String") && t2.endsWith("Bundle")) {
                            hasOnPlayFromSearch = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!hasOnPlayFromSearch) {
            context.report(ISSUE, context.getNameLocation(node), "This class should override `onPlayFromSearch(String, Bundle)` to support Android Auto voice searches");
        }
    }
}