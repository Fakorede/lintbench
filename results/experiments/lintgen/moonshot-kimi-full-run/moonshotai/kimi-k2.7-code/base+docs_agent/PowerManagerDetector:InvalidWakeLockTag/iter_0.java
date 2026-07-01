package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final int MAX_TAG_LENGTH = 100;

    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            @NotNull JavaContext context,
            @NotNull UCallExpression node,
            @NotNull JavaEvaluator evaluator) {
        if (!evaluator.isMemberInClass(node.resolve(), "android.os.PowerManager")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        if (!(tagArg instanceof ULiteralExpression)) {
            return;
        }

        Object value = ((ULiteralExpression) tagArg).getValue();
        if (value == null) {
            report(context, node, "Wake lock tag must not be null");
            return;
        }

        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            report(context, node, "Wake lock tag must not be empty");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            report(context, node, "Wake lock tag must not exceed " + MAX_TAG_LENGTH + " characters");
        } else if (!tag.contains(":")) {
            report(context, node, "Wake lock tag should follow the \"ClassName:Description\" naming convention");
        }
    }

    private void report(
            @NotNull JavaContext context,
            @NotNull UCallExpression node,
            @NotNull String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake lock tags must follow the naming conventions defined in the PowerManager documentation. "
                    + "They must not be null or empty, must be no longer than 100 characters, "
                    + "and should use the \"ClassName:Description\" format.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE),
            "https://developer.android.com/reference/android/os/PowerManager.html"
    );
}