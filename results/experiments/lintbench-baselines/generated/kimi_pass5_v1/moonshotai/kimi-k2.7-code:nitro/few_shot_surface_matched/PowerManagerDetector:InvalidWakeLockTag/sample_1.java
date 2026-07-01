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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must follow the naming conventions defined in the "
                            + "`PowerManager` documentation. Tags must be non-null, non-empty, "
                            + "and must not contain whitespace.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public PowerManagerDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        if (node.getValueArgumentCount() != 2) {
            return;
        }

        UExpression tagArgument = node.getValueArguments().get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        Object value = ((ULiteralExpression) tagArgument).getValue();
        if (value == null) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    node,
                    context.getLocation(tagArgument),
                    "Wake lock tag must not be null.");
            return;
        }

        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty() || containsWhitespace(tag)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    node,
                    context.getLocation(tagArgument),
                    "Invalid wake lock tag \"" + tag + "\". Wake lock tags must be non-empty "
                            + "and must not contain whitespace.");
        }
    }

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}