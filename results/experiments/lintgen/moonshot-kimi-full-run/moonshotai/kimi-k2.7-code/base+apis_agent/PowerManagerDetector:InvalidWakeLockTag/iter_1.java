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

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final int MAX_TAG_LENGTH = 127;

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "The tag passed to PowerManager.newWakeLock is invalid. It must not be null or empty, "
                    + "and must not exceed " + MAX_TAG_LENGTH + " characters.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        if (tagArgument instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) tagArgument).getValue();
            if (value == null) {
                report(context, tagArgument, "Wake lock tag must not be null.");
                return;
            }
        }

        String tag = ConstantEvaluator.evaluateString(context, tagArgument, false);
        if (tag == null) {
            return;
        }

        if (tag.isEmpty()) {
            report(context, tagArgument, "Wake lock tag must not be empty.");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            report(context, tagArgument,
                    "Wake lock tag length (" + tag.length()
                            + ") exceeds the maximum length of " + MAX_TAG_LENGTH
                            + " characters.");
        }
    }

    private void report(JavaContext context, UExpression node, String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}