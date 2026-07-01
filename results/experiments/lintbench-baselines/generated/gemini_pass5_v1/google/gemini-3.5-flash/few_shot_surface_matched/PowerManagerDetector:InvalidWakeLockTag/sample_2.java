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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the "
                            + "PowerManager documentation. In particular, custom tags should be "
                            + "prefixed with an application-defined prefix (e.g. \"app:my_tag\"), "
                            + "must not contain spaces, and must not exceed 127 characters.",
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
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }
        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }
        UExpression tagExpression = args.get(1);
        Object value = ConstantEvaluator.evaluate(context, tagExpression);
        if (value instanceof String) {
            String tag = (String) value;
            if (tag.isEmpty()) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagExpression,
                        context.getLocation(tagExpression),
                        "Wake Lock tag cannot be empty");
                return;
            }
            if (tag.length() > 127) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagExpression,
                        context.getLocation(tagExpression),
                        "Wake Lock tag is too long (" + tag.length() + " characters, limit is 127)");
                return;
            }
            for (int i = 0; i < tag.length(); i++) {
                char c = tag.charAt(i);
                if (Character.isWhitespace(c)) {
                    context.report(
                            INVALID_WAKE_LOCK_TAG,
                            tagExpression,
                            context.getLocation(tagExpression),
                            "Wake Lock tag must not contain spaces or whitespace characters");
                    return;
                }
            }
            if (!tag.contains(":")) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagExpression,
                        context.getLocation(tagExpression),
                        "Wake Lock tag should be prefixed with an application-defined prefix followed by a colon, e.g. \"app:my_tag\"");
            }
        }
    }
}