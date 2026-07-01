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
import org.jetbrains.uast.UastLiteralUtils;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming convention `\"*:*\"` as described in "
                            + "the `PowerManager` documentation. The tag must contain a colon "
                            + "separating a prefix (typically your app's package name or a short "
                            + "identifier) from a descriptive name.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        // newWakeLock(int levelAndFlags, String tag)
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            // We can only validate literal string tags
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!UastLiteralUtils.isStringLiteral(literal)) {
            return;
        }

        String tag = (String) literal.getValue();
        if (tag == null) {
            return;
        }

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Tag name `"
                            + tag
                            + "` does not look right: it should follow the `\"*:*\"` pattern "
                            + "(a colon separating a prefix from a descriptive tag name)");
        }
    }

    /**
     * Validates that a wake lock tag follows the naming convention described in the PowerManager
     * documentation. The tag must be of the form {@code "prefix:name"} where both prefix and name
     * are non-empty strings containing no whitespace.
     *
     * @param tag the wake lock tag string to validate
     * @return {@code true} if the tag is valid, {@code false} otherwise
     */
    private static boolean isValidWakeLockTag(@NonNull String tag) {
        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0) {
            // No colon found, or colon is the very first character (empty prefix)
            return false;
        }

        if (colonIndex == tag.length() - 1) {
            // Colon is the last character (empty name after colon)
            return false;
        }

        // Check that there is no whitespace in the tag
        for (int i = 0; i < tag.length(); i++) {
            if (Character.isWhitespace(tag.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}