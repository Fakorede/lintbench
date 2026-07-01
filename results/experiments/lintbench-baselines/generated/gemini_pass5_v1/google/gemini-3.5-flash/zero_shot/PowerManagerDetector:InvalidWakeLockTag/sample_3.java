package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "InvalidWakeLockTag",
        "Invalid Wake Lock Tag",
        "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
        "The tag should be a class name, or at least have a prefix followed by a colon and a suffix " +
        "(e.g. \"MyClass:myTag\"). It should also not contain whitespace.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(
            PowerManagerDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }

        UExpression tagArg = valueArguments.get(1);
        Object evaluated = tagArg.evaluate();
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            validateTag(context, tagArg, tag);
        }
    }

    private void validateTag(@NotNull JavaContext context, @NotNull UExpression tagArg, @NotNull String tag) {
        if (tag.isEmpty()) {
            context.report(
                ISSUE,
                tagArg,
                context.getLocation(tagArg),
                "Wake lock tag cannot be empty"
            );
            return;
        }

        if (hasWhitespace(tag)) {
            context.report(
                ISSUE,
                tagArg,
                context.getLocation(tagArg),
                "Wake lock tag should not contain spaces"
            );
            return;
        }

        if (!tag.contains(":")) {
            context.report(
                ISSUE,
                tagArg,
                context.getLocation(tagArg),
                "Wake lock tag should follow the naming convention of prefixing with a class name, e.g. \"MyClass:myTag\""
            );
            return;
        }

        String[] parts = tag.split(":", 2);
        String prefix = parts[0];
        String suffix = parts[1];

        if (prefix.isEmpty()) {
            context.report(
                ISSUE,
                tagArg,
                context.getLocation(tagArg),
                "Wake lock tag prefix cannot be empty"
            );
        } else if (suffix.isEmpty()) {
            context.report(
                ISSUE,
                tagArg,
                context.getLocation(tagArg),
                "Wake lock tag suffix cannot be empty"
            );
        }
    }

    private static boolean hasWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}