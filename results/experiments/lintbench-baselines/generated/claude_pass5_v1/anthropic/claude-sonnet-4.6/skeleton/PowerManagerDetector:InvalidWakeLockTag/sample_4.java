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
                    "Wake Lock tags must follow the format described in the `PowerManager` "
                            + "documentation: the tag must not be null or empty, and it must "
                            + "follow the `*TagName*` convention (i.e. contain a colon separating "
                            + "a package-like prefix from the tag name, e.g. `MyApp:MyWakeLock`).",
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
        // newWakeLock(int levelAndFlags, String tag) — tag is the second argument
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // We can only validate string literals at compile time
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        Object value = literal.getValue();

        if (value == null) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be null");
            return;
        }

        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    String.format(
                            "Wake Lock tag `\"%s\"` looks incorrect: it should be in the "
                                    + "format `*packagename*:*tag*` (e.g. `MyApp:MyWakeLock`)",
                            tag));
        }
    }

    /**
     * Validates that the wake lock tag follows the naming convention required by PowerManager.
     *
     * <p>According to the Android documentation, the tag should be of the form
     * "*packagename*:*tag*" where *packagename* is a non-empty prefix and *tag* is a
     * non-empty suffix separated by a colon.
     *
     * @param tag the wake lock tag string to validate
     * @return true if the tag is valid, false otherwise
     */
    private static boolean isValidWakeLockTag(@NonNull String tag) {
        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0) {
            // No colon found, or colon is the first character (empty prefix)
            return false;
        }
        // The part after the colon must not be empty
        if (colonIndex == tag.length() - 1) {
            return false;
        }
        return true;
    }
}