package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
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
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "intent-filter for the action `onPlayFromSearch`, you also need to override " +
            "and implement `onPlayFromSearch(String query, Bundle bundle)`.\n\n" +
            "Reference documentation:\n" +
            "https://developer.android.com/training/auto/audio/index.html#support_voice",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String MEDIA_SESSION_CALLBACK = "android.media.session.MediaSession.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK = "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String ANDROIDX_MEDIA_SESSION_COMPAT_CALLBACK = "androidx.media.session.MediaSessionCompat.Callback";
    private static final String METHOD_NAME = "onPlayFromSearch";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isAbstract() || node.isInterface()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isCallback = evaluator.extendsClass(node, MEDIA_SESSION_CALLBACK, true)
                        || evaluator.extendsClass(node, MEDIA_SESSION_COMPAT_CALLBACK, true)
                        || evaluator.extendsClass(node, ANDROIDX_MEDIA_SESSION_COMPAT_CALLBACK, true);

                if (!isCallback) {
                    return;
                }

                if (hasOnPlayFromSearch(node, evaluator)) {
                    return;
                }

                context.report(ISSUE, node, context.getLocation(node),
                        "Override `onPlayFromSearch` to support Android Auto voice search");
            }
        };
    }

    private boolean hasOnPlayFromSearch(UClass node, JavaEvaluator evaluator) {
        for (UMethod method : node.getMethods()) {
            if (METHOD_NAME.equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    if (parameters.get(0).getType() != null && parameters.get(1).getType() != null
                            && evaluator.typeMatches(parameters.get(0).getType(), "java.lang.String")
                            && evaluator.typeMatches(parameters.get(1).getType(), "android.os.Bundle")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}