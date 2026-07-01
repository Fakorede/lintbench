package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an intent-filter for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.\n" +
            "Reference: https://developer.android.com/training/auto/audio/index.html#support_voice",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String MEDIA_SESSION_CALLBACK = "android.media.session.MediaSession.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK_V4 = "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX = "androidx.media.session.MediaSessionCompat.Callback";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface() || context.getEvaluator().isAbstract(node)) {
                    return;
                }

                boolean extendsCallback = context.getEvaluator().extendsClass(node, MEDIA_SESSION_CALLBACK, false)
                        || context.getEvaluator().extendsClass(node, MEDIA_SESSION_COMPAT_CALLBACK_V4, false)
                        || context.getEvaluator().extendsClass(node, MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX, false);

                if (!extendsCallback) {
                    return;
                }

                boolean hasOnPlayFromSearch = false;
                for (UMethod method : node.getMethods()) {
                    if ("onPlayFromSearch".equals(method.getName())) {
                        List<UParameter> params = method.getUastParameters();
                        if (params.size() == 2) {
                            hasOnPlayFromSearch = true;
                            break;
                        }
                    }
                }

                if (!hasOnPlayFromSearch) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "This class should override `onPlayFromSearch` to support Android Auto voice searches");
                }
            }
        };
    }
}