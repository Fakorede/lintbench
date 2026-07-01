package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE_MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, your MediaSession.Callback "
                    + "(or MediaSessionCompat.Callback) must override "
                    + "`onPlayFromSearch(String, Bundle)`.\n\n"
                    + "See https://developer.android.com/training/auto/audio/index.html#support_voice",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass cls) {
        if (cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasOverride = false;
        for (PsiMethod method : cls.getMethods()) {
            if (!"onPlayFromSearch".equals(method.getName())) {
                continue;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 2) {
                continue;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            if (isString(parameters[0]) && isBundle(parameters[1])) {
                hasOverride = true;
                break;
            }
        }

        if (!hasOverride) {
            Location location = context.getNameLocation(cls);
            String message = "To support voice searches on Android Auto, override "
                    + "onPlayFromSearch(String, Bundle) in this MediaSession callback";
            context.report(ISSUE_MISSING_ON_PLAY_FROM_SEARCH, cls, location, message);
        }
    }

    private static boolean isString(PsiParameter parameter) {
        String type = parameter.getType().getCanonicalText();
        return "java.lang.String".equals(type) || "String".equals(type);
    }

    private static boolean isBundle(PsiParameter parameter) {
        String type = parameter.getType().getCanonicalText();
        return "android.os.Bundle".equals(type) || "Bundle".equals(type);
    }
}