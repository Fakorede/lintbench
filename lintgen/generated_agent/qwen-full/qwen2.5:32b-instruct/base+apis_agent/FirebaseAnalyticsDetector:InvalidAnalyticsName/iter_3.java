package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class FirebaseAnalyticsDetector extends Detector implements SourceCodeScanner {

    public static final Issue INVALID_ANALYTICS_NAME = Issue.create(
            "InvalidAnalyticsName",
            "Event names and parameters must follow the naming conventions defined in the `FirebaseAnalytics#logEvent()` documentation.",
            "Event names and parameters should be valid according to Firebase Analytics guidelines. Invalid names can lead to issues with tracking and reporting.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    FirebaseAnalyticsDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("logEvent");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("logEvent")) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty() && args.get(0) instanceof ULiteralExpression) {
                String eventName = ((ULiteralExpression) args.get(0)).getValue().toString();
                if (!isValidAnalyticsName(eventName)) {
                    context.report(INVALID_ANALYTICS_NAME, node, context.getLocation(node),
                            "Event name '" + eventName + "' is invalid. Event names must follow the naming conventions defined in Firebase Analytics documentation.");
                }
            }
        }
    }

    private boolean isValidAnalyticsName(String name) {
        // Simple validation logic: event names should not contain spaces or special characters
        return name.matches("^[a-zA-Z0-9_]+$");
    }
}