package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
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
        "To support voice searches on Android Auto, in addition to adding an intent-filter for the action, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.\n" +
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
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().extendsClass(node, "android.media.session.MediaSession.Callback", false) ||
                    context.getEvaluator().extendsClass(node, "android.support.v4.media.session.MediaSessionCompat.Callback", false) ||
                    context.getEvaluator().extendsClass(node, "androidx.media.session.MediaSessionCompat.Callback", false)) {

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
                            "Missing `onPlayFromSearch` implementation for Android Auto voice search support");
                    }
                }
            }
        };
    }
}