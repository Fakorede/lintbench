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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_OS_POWER_MANAGER = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK = "newWakeLock";

    public static final Issue INVALID_WAKE_LOCK_TAG = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the PowerManager documentation: "
                    + "they should be a unique identifier for this wake lock within the application, "
                    + "use the recommended `*:tag` format, and be no longer than 50 characters.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList(NEW_WAKE_LOCK);
    }

    @Override
    public void visitMethodCall(
            @NotNull JavaContext context,
            @NotNull UCallExpression call,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, ANDROID_OS_POWER_MANAGER)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        String tag = getStringLiteralValue(tagArgument);
        if (tag == null) {
            return;
        }

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArgument,
                    context.getLocation(tagArgument),
                    "Invalid Wake Lock tag \"" + tag + "\". Tags should use the `*:tag` format "
                            + "and be no longer than 50 characters.");
        }
    }

    private static String getStringLiteralValue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    private static boolean isValidWakeLockTag(@NotNull String tag) {
        return !tag.isEmpty()
                && tag.contains(":")
                && !tag.startsWith(":")
                && !tag.endsWith(":")
                && !tag.contains(" ")
                && tag.length() <= 50;
    }
}