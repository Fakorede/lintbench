package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingOnPlayFromSearch",
        "Missing `onPlayFromSearch`",
        "To support voice searches on Android Auto, in addition to adding an " +
        "`intent-filter` for the action `onPlayFromSearch`, you also need to override " +
        "and implement `onPlayFromSearch(String query, Bundle bundle)`\n\n" +
        "Reference documentation:\n" +
        "https://developer.android.com/training/auto/audio/index.html#support_voice",
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
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                JavaEvaluator evaluator = context.getEvaluator();
                boolean isCallback = evaluator.extendsClass(node, "android.media.session.MediaSession.Callback", false) ||
                                     evaluator.extendsClass(node, "android.support.v4.media.session.MediaSessionCompat.Callback", false) ||
                                     evaluator.extendsClass(node, "androidx.media.session.MediaSessionCompat.Callback", false);
                if (!isCallback) {
                    return;
                }

                boolean hasOnPlayFromSearch = false;
                for (PsiMethod method : node.getMethods()) {
                    if ("onPlayFromSearch".equals(method.getName())) {
                        PsiParameter[] params = method.getParameterList().getParameters();
                        if (params.length == 2) {
                            String type1 = params[0].getType().getCanonicalText();
                            String type2 = params[1].getType().getCanonicalText();
                            if ("java.lang.String".equals(type1) && "android.os.Bundle".equals(type2)) {
                                hasOnPlayFromSearch = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasOnPlayFromSearch) {
                    Location location = context.getNameLocation(node);
                    context.report(ISSUE, node, location,
                        "Missing `onPlayFromSearch(String, Bundle)`: To support voice searches on Android Auto, " +
                        "override and implement this method.");
                }
            }
        };
    }
}