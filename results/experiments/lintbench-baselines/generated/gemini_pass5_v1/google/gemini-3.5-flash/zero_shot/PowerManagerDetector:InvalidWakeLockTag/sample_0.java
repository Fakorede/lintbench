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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "They must not contain spaces or control characters, and should be prefixed with a unique prefix " +
            "separated by a colon (e.g. `MyApp:MyTag`) or be a class name.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberOf(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }

        UExpression tagArgument = valueArguments.get(1);
        Object evaluated = ConstantEvaluator.evaluate(context, tagArgument);
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            validateTag(context, tagArgument, tag);
        }
    }

    private void validateTag(@NotNull JavaContext context, @NotNull UExpression argument, @NotNull String tag) {
        if (tag.isEmpty()) {
            context.report(ISSUE, argument, context.getLocation(argument), "Wake lock tag cannot be empty.");
            return;
        }

        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                context.report(ISSUE, argument, context.getLocation(argument),
                        "Wake lock tags must not contain spaces or control characters.");
                return;
            }
        }

        if (!tag.contains(":") && !tag.contains(".")) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tags should be prefixed with a unique prefix separated by a colon (e.g. `MyApp:MyTag`) " +
                    "or be a fully qualified class name.");
        }
    }
}