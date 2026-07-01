package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
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
            "To support voice searches on Android Auto, in addition to adding an intent-filter for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.\n\n" +
            "Reference: https://developer.android.com/training/auto/audio/index.html#support_voice",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String MEDIA_SESSION_CALLBACK = "android.media.session.MediaSession.Callback";
    private static final String SUPPORT_MEDIA_SESSION_CALLBACK = "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String ANDROIDX_MEDIA_SESSION_CALLBACK = "androidx.media.session.MediaSessionCompat.Callback";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isAbstract() || node.isInterface()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isCallback = evaluator.extendsClass(node, MEDIA_SESSION_CALLBACK, false)
                        || evaluator.extendsClass(node, SUPPORT_MEDIA_SESSION_CALLBACK, false)
                        || evaluator.extendsClass(node, ANDROIDX_MEDIA_SESSION_CALLBACK, false);

                if (!isCallback) {
                    return;
                }

                boolean hasOverride = false;
                for (UMethod method : node.getMethods()) {
                    if ("onPlayFromSearch".equals(method.getName())) {
                        List<UParameter> params = method.getUastParameters();
                        if (params.size() == 2) {
                            boolean isString = evaluator.typeMatches(params.get(0).getType(), "java.lang.String");
                            boolean isBundle = evaluator.typeMatches(params.get(1).getType(), "android.os.Bundle");
                            if (isString && isBundle) {
                                hasOverride = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasOverride) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "This class should override `onPlayFromSearch(String, Bundle)` to support Android Auto voice search");
                }
            }
        };
    }
}