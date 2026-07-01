package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingOnPlayFromSearch",
        "Missing onPlayFromSearch",
        "Missing `onPlayFromSearch`\n\n" +
        "To support voice searches on Android Auto, in addition to adding an  `intent-filter` for the action `onPlayFromSearch`,  you also need to override and implement  `onPlayFromSearch(String query, Bundle bundle)`\n\n" +
        "Reference documentation:\n" +
        "  - https://developer.android.com/training/auto/audio/index.html#support_voice",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isAbstract() || node.isInterface()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isCallback = evaluator.extendsClass(node, "android.media.session.MediaSession.Callback", true)
                        || evaluator.extendsClass(node, "android.support.v4.media.session.MediaSessionCompat.Callback", true)
                        || evaluator.extendsClass(node, "androidx.media.session.MediaSessionCompat.Callback", true);

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
                            if (type1 != null && type2 != null
                                    && "java.lang.String".equals(type1.getCanonicalText())
                                    && "android.os.Bundle".equals(type2.getCanonicalText())) {
                                hasOnPlayFromSearch = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasOnPlayFromSearch) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                        "Missing `onPlayFromSearch` implementation for Android Auto voice search support");
                }
            }
        };
    }
}