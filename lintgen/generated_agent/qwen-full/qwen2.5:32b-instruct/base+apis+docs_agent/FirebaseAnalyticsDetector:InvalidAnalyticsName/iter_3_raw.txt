package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class FirebaseAnalyticsDetector extends Detector implements SourceCodeScanner {

    public static final Issue INVALID_ANALYTICS_NAME = Issue.create(
            "InvalidAnalyticsName",
            "Event names and parameters must follow the naming conventions defined in the `FirebaseAnalytics#logEvent()` documentation.",
            "Ensure that event names and parameter keys are valid according to Firebase Analytics guidelines.",
            Category.CORRECTNESS,
            6, // Priority
            Severity.WARNING,
            new Implementation(
                    FirebaseAnalyticsDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("logEvent");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("logEvent")) {
            checkLogEvent(context, node);
        }
    }

    private void checkLogEvent(@NonNull JavaContext context, @NonNull UCallExpression callExpr) {
        List<UExpression> args = callExpr.getValueArguments();
        if (args.size() >= 1 && args.get(0) instanceof ULiteralExpression) {
            String eventName = ((ULiteralExpression) args.get(0)).getValue().toString();

            // Check for invalid characters in event name
            if (!eventName.matches("^[a-zA-Z0-9_]+$")) {
                Location location = context.getLocation(callExpr);
                context.report(INVALID_ANALYTICS_NAME, callExpr, location,
                        "Event name contains invalid characters. Event names must only contain alphanumeric and underscore characters.");
            }
        }
    }

}