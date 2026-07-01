package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
            "They should be package-prefixed (e.g. `com.example.myapp:mylock`), and must not contain spaces " +
            "or invalid characters.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9._\\-/:<>@]+$");

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }
        UExpression tagArgument = arguments.get(1);
        Object evaluated = ConstantEvaluator.evaluate(context, tagArgument);
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            validateTag(context, tagArgument, tag);
        }
    }

    private void validateTag(@NonNull JavaContext context, @NonNull UExpression argument, @NonNull String tag) {
        if (tag.isEmpty()) {
            context.report(ISSUE, argument, context.getLocation(argument), "Wake lock tag cannot be empty");
            return;
        }

        if (tag.contains(" ")) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tags should not contain spaces: \"" + tag + "\"");
            return;
        }

        if (!tag.contains(":")) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tags should be package-prefixed (e.g. 'com.example.myapp:mylock') to avoid collisions: \"" + tag + "\"");
            return;
        }

        if (!VALID_CHARS.matcher(tag).matches()) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tag contains invalid characters: \"" + tag + "\". " +
                    "Use only alphanumeric characters, dots, underscores, hyphens, slashes, colons, '<', '>', or '@'.");
            return;
        }

        int colonIndex = tag.indexOf(':');
        String prefix = tag.substring(0, colonIndex);
        String suffix = tag.substring(colonIndex + 1);

        if (prefix.isEmpty() || suffix.isEmpty()) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tag should have both a package prefix and a lock name: \"" + tag + "\"");
            return;
        }

        if (!prefix.contains(".")) {
            context.report(ISSUE, argument, context.getLocation(argument),
                    "Wake lock tag prefix should be a valid package name (containing at least one '.'): \"" + tag + "\"");
        }
    }
}