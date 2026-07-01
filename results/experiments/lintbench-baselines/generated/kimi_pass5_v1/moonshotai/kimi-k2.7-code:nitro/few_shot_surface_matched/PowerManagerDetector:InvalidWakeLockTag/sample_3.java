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

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 23;

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake lock tags passed to `PowerManager.newWakeLock()` must be "
                                    + "non-empty string literals and must follow the naming "
                                    + "conventions described in the `PowerManager` documentation. "
                                    + "Tags are typically limited to "
                                    + MAX_TAG_LENGTH
                                    + " characters.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public PowerManagerDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getContainingClass() == null
                || !POWER_MANAGER_CLASS.equals(method.getContainingClass().getQualifiedName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        if (!(tagArg instanceof ULiteralExpression)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must be a string literal");
            return;
        }

        Object value = ((ULiteralExpression) tagArg).getValue();
        if (!(value instanceof String)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must be a string");
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must not be empty");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must not be longer than "
                            + MAX_TAG_LENGTH
                            + " characters");
        }
    }
}