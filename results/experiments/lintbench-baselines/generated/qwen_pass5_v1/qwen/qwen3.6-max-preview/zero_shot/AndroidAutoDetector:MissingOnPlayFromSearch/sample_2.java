package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch implementation",
            "To support voice searches on Android Auto, in addition to adding an  `intent-filter` for the action `onPlayFromSearch`,  you also need to override and implement  `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE));

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
                boolean isCallback = evaluator.extendsClass(node, "android.media.session.MediaSession.Callback", true)
                        || evaluator.extendsClass(node, "android.support.v4.media.session.MediaSessionCompat.Callback", true)
                        || evaluator.extendsClass(node, "androidx.media.session.MediaSessionCompat.Callback", true);

                if (!isCallback) {
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
                    context.report(ISSUE, node, context.getLocation(node),
                            "Missing `onPlayFromSearch(String, Bundle)` override required for Android Auto voice search support");
                }
            }
        };
    }
}