package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class FirebaseAnalyticsDetector extends Detector implements Detector.JavaContext {

    public static final Issue INVALID_ANALYTICS_NAME = Issue.create(
            "InvalidAnalyticsName",
            "Event names and parameters must follow the naming conventions defined in the `FirebaseAnalytics#logEvent()` documentation.",
            "Event names and parameters should only contain lowercase alphanumeric characters, underscores, or periods. They cannot start with an underscore or period.",
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
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if (node != null && "logEvent".equals(node.getMethodName())) {
            checkLogEvent(context, node);
        }
    }

    private void checkLogEvent(@NonNull JavaContext context, @NonNull UCallExpression call) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() >= 1 && arguments.get(0) instanceof ULiteralExpression) {
            ULiteralExpression eventNameArg = (ULiteralExpression) arguments.get(0);
            String eventName = eventNameArg.getSourcePsi().getText();

            if (!isValidAnalyticsName(eventName)) {
                Location location = context.getLocation(call);
                context.report(INVALID_ANALYTICS_NAME, call, location,
                        "Event name '%s' is invalid. It should only contain lowercase alphanumeric characters, underscores, or periods.", eventName);
            }
        }
    }

    private boolean isValidAnalyticsName(@NonNull String name) {
        if (name.isEmpty() || name.startsWith("_") || name.startsWith(".")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
                return false;
            }
        }
        return true;
    }

}