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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final int MAX_WAKE_LOCK_TAG_LENGTH = 127;

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake lock tags must be non-empty and must not exceed 127 characters in length, "
                    + "as documented in `android.os.PowerManager`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        Object value = new ConstantEvaluator(context).evaluate(tagArgument);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tags must not be empty");
        } else if (tag.length() > MAX_WAKE_LOCK_TAG_LENGTH) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag length (" + tag.length()
                            + ") exceeds the maximum allowed length of "
                            + MAX_WAKE_LOCK_TAG_LENGTH + " characters");
        }
    }
}