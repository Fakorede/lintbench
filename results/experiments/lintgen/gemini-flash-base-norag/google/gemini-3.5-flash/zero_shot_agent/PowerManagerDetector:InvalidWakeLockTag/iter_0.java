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
        "The tag should be prefixed with your app's package name or a unique prefix followed by a colon, " +
        "for example `com.example.myapp:MyTag`.",
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

    private void validateTag(@NotNull JavaContext context, @NotNull UExpression argument, @NotNull String tag) {
        if (tag.isEmpty()) {
            context.report(ISSUE, argument, context.getLocation(argument), "Wake lock tag cannot be empty");
            return;
        }

        int colonIndex = tag.indexOf(':');
        if (colonIndex == -1) {
            context.report(ISSUE, argument, context.getLocation(argument),
                "Wake lock tag should be prefixed with your app's package name or a unique prefix followed by a colon, e.g. `com.example.myapp:MyTag`"
            );
            return;
        }

        if (colonIndex == 0) {
            context.report(ISSUE, argument, context.getLocation(argument),
                "Wake lock tag prefix cannot be empty"
            );
            return;
        }

        if (colonIndex == tag.length() - 1) {
            context.report(ISSUE, argument, context.getLocation(argument),
                "Wake lock tag suffix cannot be empty"
            );
        }
    }
}