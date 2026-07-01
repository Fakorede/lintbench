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
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 23;

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must follow the naming conventions defined in the PowerManager "
                            + "documentation: they must not be empty, must not start or end with "
                            + "whitespace, and should be no longer than 23 characters.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    public PowerManagerDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getContainingClass() == null
                || !POWER_MANAGER.equals(method.getContainingClass().getQualifiedName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        if (!(tagArg instanceof ULiteralExpression)) {
            return;
        }

        Object value = ((ULiteralExpression) tagArg).getValue();
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must not be empty");
            return;
        }

        if (tag.length() > MAX_TAG_LENGTH) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag \"" + tag + "\" is too long (" + tag.length()
                            + " characters); it should be no longer than " + MAX_TAG_LENGTH
                            + " characters");
            return;
        }

        if (Character.isWhitespace(tag.charAt(0))
                || Character.isWhitespace(tag.charAt(tag.length() - 1))) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must not start or end with whitespace");
        }
    }
}