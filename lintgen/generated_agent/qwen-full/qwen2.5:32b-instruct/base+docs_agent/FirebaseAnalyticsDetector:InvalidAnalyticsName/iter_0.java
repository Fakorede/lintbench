package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethodCallExpression;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class FirebaseAnalyticsDetector extends Detector implements Detector.UastScanner {

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

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("logEvent");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UElement element, @NonNull PsiMethodCallExpression methodCall) {
        if (element instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) element;
            USimpleNameReferenceExpression qualifier = call.getMethodName();
            String methodName = qualifier.getIdentifier();

            if ("logEvent".equals(methodName)) {
                checkLogEvent(context, call);
            }
        }
    }

    private void checkLogEvent(@NonNull JavaContext context, @NonNull UCallExpression call) {
        List<UElement> arguments = call.getValueArguments();
        if (arguments.size() >= 1 && arguments.get(0) instanceof USimpleNameReferenceExpression) {
            USimpleNameReferenceExpression eventNameArg = (USimpleNameReferenceExpression) arguments.get(0);
            String eventName = eventNameArg.getIdentifier();

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