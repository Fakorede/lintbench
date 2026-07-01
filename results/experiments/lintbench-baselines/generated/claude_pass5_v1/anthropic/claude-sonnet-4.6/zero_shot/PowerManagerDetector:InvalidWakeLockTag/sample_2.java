package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming convention " +
            "`PackageName:TagName` as described in the `PowerManager` documentation.\n\n" +
            "The tag must:\n" +
            "- Not be null or empty\n" +
            "- Contain a colon (`:`) separating the package name from the tag name\n" +
            "- Not contain any whitespace characters\n\n" +
            "Reference: https://developer.android.com/reference/android/os/PowerManager.html",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        if (!(tagArgument instanceof ULiteralExpression)) {
            // We can only check literal string values
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!literal.isString()) {
            return;
        }

        String tag = (String) UastLiteralUtils.getValueIfStringLiteral(literal);
        if (tag == null) {
            return;
        }

        validateTag(context, call, tagArgument, tag);
    }

    private void validateTag(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull UExpression tagExpression,
            @NonNull String tag) {

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag must not be empty"
            );
            return;
        }

        // Check for whitespace characters
        for (int i = 0; i < tag.length(); i++) {
            if (Character.isWhitespace(tag.charAt(i))) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(tagExpression),
                        "Wake Lock tag must not contain whitespace characters; " +
                        "the recommended format is `PackageName:TagName`"
                );
                return;
            }
        }

        // Check that the tag contains a colon separating package name from tag name
        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag `" + tag + "` does not look right; " +
                    "the recommended format is `PackageName:TagName`"
            );
            return;
        }

        // Check that the colon is not at the start or end (both parts must be non-empty)
        int colonIndex = tag.indexOf(':');
        if (colonIndex == 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag `" + tag + "` is missing the package name part; " +
                    "the recommended format is `PackageName:TagName`"
            );
            return;
        }

        if (colonIndex == tag.length() - 1) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag `" + tag + "` is missing the tag name part; " +
                    "the recommended format is `PackageName:TagName`"
            );
        }
    }
}