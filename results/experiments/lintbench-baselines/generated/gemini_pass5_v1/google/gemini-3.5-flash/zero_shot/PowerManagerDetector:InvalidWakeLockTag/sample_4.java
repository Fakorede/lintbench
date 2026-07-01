package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
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
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "Tag names should be constant and static, and must not contain spaces or control characters. " +
            "They should ideally be prefixed with the app/package name followed by a colon.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static final Pattern VALID_TAG_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]+$");

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }

        UExpression tagExpression = valueArguments.get(1);
        Object evaluated = ConstantEvaluator.evaluate(context, tagExpression);

        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            validateTag(context, tagExpression, tag);
        } else {
            // Wake lock tag is dynamic (not evaluated to a constant string)
            context.report(
                    ISSUE,
                    tagExpression,
                    context.getLocation(tagExpression),
                    "Avoid using dynamic wake lock tags. Tag names should be constant and static."
            );
        }
    }

    private void validateTag(JavaContext context, UExpression expression, String tag) {
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    expression,
                    context.getLocation(expression),
                    "Wake lock tag cannot be empty"
            );
            return;
        }

        if (tag.contains(" ")) {
            context.report(
                    ISSUE,
                    expression,
                    context.getLocation(expression),
                    "Wake lock tag must not contain spaces"
            );
            return;
        }

        if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
            context.report(
                    ISSUE,
                    expression,
                    context.getLocation(expression),
                    "Wake lock tag must not contain control characters or special characters"
            );
        }
    }
}