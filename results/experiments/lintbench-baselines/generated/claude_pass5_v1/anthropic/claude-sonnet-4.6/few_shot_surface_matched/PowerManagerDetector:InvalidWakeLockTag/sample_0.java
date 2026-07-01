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

    private static final String POWER_MANAGER = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. Specifically, the tag must "
                                    + "follow the format `*TagName*` (using a colon separator like "
                                    + "`MyApp:MyWakeLock`) and must not be null or empty. "
                                    + "See https://developer.android.com/reference/android/os/PowerManager.html "
                                    + "for details.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public PowerManagerDetector() {}

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
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        if (!(tagArgument instanceof ULiteralExpression)) {
            // We can only validate string literals at lint time
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!literal.isString()) {
            return;
        }

        Object value = UastLiteralUtils.getValueIfStringLiteral(literal);
        if (value == null) {
            return;
        }

        String tag = value.toString();

        if (tag.isEmpty()) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        // According to PowerManager documentation, the tag should be of the form
        // "*packagename*:*tag*" (i.e., contain a colon separator).
        if (!tag.contains(":")) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `"
                            + tag
                            + "` should follow the naming convention "
                            + "`packageName:tag` (containing a colon separator), "
                            + "as required by the `PowerManager` documentation");
        }
    }
}