package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.util.UastExpressionUtils;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";
    private static final int MAX_TAG_LENGTH = 127;
    private static final Pattern VALID_TAG_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_\\.\\-]*$");

    private static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the PowerManager "
                    + "documentation: they must be non-null, non-empty, at most "
                    + MAX_TAG_LENGTH
                    + " characters long, and consist of alphanumeric characters, '.', '_', and '-' only.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    @NotNull
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NotNull JavaContext context,
            @NotNull UCallExpression node,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        if (node.getValueArgumentCount() != 2) {
            return;
        }

        UExpression tagArg = node.getValueArguments().get(1);
        if (UastExpressionUtils.isNullLiteral(tagArg)) {
            reportIssue(context, tagArg, "Wake lock tag must not be null.");
            return;
        }

        Object value = new ConstantEvaluator(context).evaluate(tagArg);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            reportIssue(context, tagArg, "Wake lock tag must not be empty.");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            reportIssue(context, tagArg,
                    "Wake lock tag is too long (" + tag.length() + " characters, max " + MAX_TAG_LENGTH + ").");
        } else if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
            reportIssue(context, tagArg,
                    "Wake lock tag \"" + tag + "\" contains invalid characters.");
        }
    }

    private static void reportIssue(
            @NotNull JavaContext context,
            @NotNull UExpression node,
            @NotNull String message) {
        Location location = context.getLocation(node);
        context.report(ISSUE, location, message);
    }
}