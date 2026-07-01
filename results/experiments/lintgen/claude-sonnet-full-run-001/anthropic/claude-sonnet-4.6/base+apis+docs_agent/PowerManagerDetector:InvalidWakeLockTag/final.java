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
                            + "`*TagName*` where `*` is a package name or class name prefix "
                            + "followed by a colon. For example: `MyApp:MyWakeLockTag`.\n"
                            + "\n"
                            + "Reference: https://developer.android.com/reference/android/os/PowerManager.html",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            // We can only check string literals
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
                    "The wake lock tag `\""
                            + tag
                            + "\"` does not look right; it should typically be of the form "
                            + "`\"MyApp:MyWakeLockTag\"`");
        }
    }

    /**
     * Checks whether the given wake lock tag is valid according to PowerManager conventions.
     *
     * <p>The tag must contain a colon separating a package/class prefix from the tag name.
     * Both the prefix and the tag name must be non-empty.
     */
    private static boolean isValidWakeLockTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }

        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0) {
            // No colon, or colon at the very beginning
            return false;
        }

        if (colonIndex == tag.length() - 1) {
            // Colon at the very end, nothing after it
            return false;
        }

        return true;
    }
}