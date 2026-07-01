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

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "PowerManager documentation. Tags should be descriptive, "
                                    + "non-empty, and must not contain whitespace.",
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
    public void visitMethodCall(
            JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        if (tagArg instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) tagArg).getValue();
            if (value == null) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must not be null");
                return;
            }
        }

        String tag = UastLiteralUtils.evaluateString(tagArg);
        if (tag == null) {
            return;
        }

        if (tag.isEmpty()) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag must not be empty");
            return;
        }

        for (int i = 0; i < tag.length(); i++) {
            if (Character.isWhitespace(tag.charAt(i))) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must not contain whitespace");
                return;
            }
        }
    }
}