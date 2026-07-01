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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the "
                            + "`PowerManager` documentation. The tag must be of the form "
                            + "`*:*` (i.e., it must contain a colon separating a package-like "
                            + "prefix from a descriptive name).\n"
                            + "\n"
                            + "See https://developer.android.com/reference/android/os/PowerManager.html "
                            + "for details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/os/PowerManager.html");

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    public PowerManagerDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        // newWakeLock(int levelAndFlags, String tag)
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            // Can't evaluate non-literal tags statically
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

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` does not look right: it should be of the form `\"package:tag\"`");
        }
    }

    /**
     * Validates that a wake lock tag follows the required naming convention.
     *
     * <p>According to the PowerManager documentation, the tag should be of the form
     * {@code *:*}, meaning it must contain at least one colon with non-empty content
     * on both sides.
     */
    private static boolean isValidWakeLockTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0) {
            // No colon, or colon is the first character (empty prefix)
            return false;
        }
        if (colonIndex == tag.length() - 1) {
            // Colon is the last character (empty suffix)
            return false;
        }
        return true;
    }
}