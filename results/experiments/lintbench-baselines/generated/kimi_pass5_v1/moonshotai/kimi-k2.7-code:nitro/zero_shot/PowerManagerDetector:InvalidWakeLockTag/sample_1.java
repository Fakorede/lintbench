package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 50;

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        Object value = ((ULiteralExpression) tagArgument).getValue();
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        if (tag.isEmpty()) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag must not be empty");
            return;
        }

        if (tag.length() > MAX_TAG_LENGTH) {
            String message = String.format(
                    "Wake lock tag `%1$s` too long (%2$d characters; maximum is %3$d)",
                    tag, tag.length(), MAX_TAG_LENGTH);
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument), message);
            return;
        }

        if (tag.contains(" ")) {
            String message = String.format("Wake lock tag `%1$s` must not contain spaces", tag);
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument), message);
            return;
        }

        if (!tag.contains(":")) {
            String message = String.format(
                    "Wake lock tag `%1$s` should be namespaced with a colon separator, "
                            + "for example `com.example.app:MyService`", tag);
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument), message);
        }
    }
}