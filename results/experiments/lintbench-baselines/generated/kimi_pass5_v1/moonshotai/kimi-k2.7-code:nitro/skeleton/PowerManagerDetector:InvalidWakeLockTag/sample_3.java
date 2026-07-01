package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 127;

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must follow the PowerManager naming conventions: "
                            + "they must be non-null, non-empty, and no longer than "
                            + MAX_TAG_LENGTH + " characters.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        PsiMethod containingMethod = node.resolve(); // not used, but kept for clarity
        PsiMethod resolvedMethod = node.resolve();
        if (resolvedMethod == null) {
            resolvedMethod = method;
        }

        if (resolvedMethod.getContainingClass() == null
                || !POWER_MANAGER_CLASS.equals(resolvedMethod.getContainingClass().getQualifiedName())) {
            return;
        }

        if (resolvedMethod.getParameterList().getParametersCount() != 2) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (tagArgument == null) {
            return;
        }

        if (!(tagArgument instanceof ULiteralExpression)) {
            // Only literal tags can be statically validated.
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        Object value = literal.getValue();
        if (value == null) {
            reportIssue(context, tagArgument, "Wake lock tag must not be null.");
            return;
        }

        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            reportIssue(context, tagArgument, "Wake lock tag must not be empty.");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            reportIssue(
                    context,
                    tagArgument,
                    "Wake lock tag length (" + tag.length() + ") exceeds maximum of " + MAX_TAG_LENGTH + ".");
        }
    }

    private void reportIssue(
            @NonNull JavaContext context,
            @NonNull UExpression node,
            @NonNull String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}