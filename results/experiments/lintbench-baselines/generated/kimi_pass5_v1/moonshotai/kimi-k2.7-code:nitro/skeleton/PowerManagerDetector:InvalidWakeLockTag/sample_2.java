package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final int MAX_WAKE_LOCK_TAG_LENGTH = 127;

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must be non-empty and must be no longer than "
                            + MAX_WAKE_LOCK_TAG_LENGTH
                            + " characters, as described in the `PowerManager` documentation.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        if (node.getValueArgumentCount() != 2) {
            return;
        }

        UExpression tagArgument = node.getValueArguments().get(1);
        String tag = ConstantEvaluator.evaluateString(context, tagArgument, true);
        if (tag == null) {
            return;
        }

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake lock tags must not be empty.");
        } else if (tag.length() > MAX_WAKE_LOCK_TAG_LENGTH) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake lock tags must be no longer than "
                            + MAX_WAKE_LOCK_TAG_LENGTH
                            + " characters.");
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }
}