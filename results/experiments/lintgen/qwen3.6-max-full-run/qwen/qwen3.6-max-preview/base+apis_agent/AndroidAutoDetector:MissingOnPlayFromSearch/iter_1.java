package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Arrays;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an intent-filter for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        if (node.isInterface() || context.getEvaluator().isAbstract(node)) {
            return;
        }

        boolean hasOnPlayFromSearch = false;
        for (UMethod method : node.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    hasOnPlayFromSearch = true;
                    break;
                }
            }
        }

        if (!hasOnPlayFromSearch) {
            context.report(ISSUE, node, context.getNameLocation(node),
                    "This class should override `onPlayFromSearch(String, Bundle)` to support Android Auto voice searches");
        }
    }
}