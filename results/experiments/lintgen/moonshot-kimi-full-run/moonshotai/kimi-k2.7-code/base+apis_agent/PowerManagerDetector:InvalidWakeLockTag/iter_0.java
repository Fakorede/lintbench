package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake lock tags must be non-null and non-empty, and should be descriptive. " +
                    "Passing a null or empty tag to PowerManager.newWakeLock(...) will result " +
                    "in an IllegalArgumentException at runtime. Tags should also respect any " +
                    "length limits described in the PowerManager documentation.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        Object value = literal.getValue();

        if (value == null) {
            report(context, tagArgument, "Wake lock tag must not be null.");
        } else if (value instanceof String) {
            String tag = (String) value;
            if (tag.isEmpty()) {
                report(context, tagArgument, "Wake lock tag must not be empty.");
            } else if (tag.length() > 127) {
                report(context, tagArgument, "Wake lock tag exceeds the maximum length of 127 characters.");
            }
        }
    }

    private void report(JavaContext context, UExpression node, String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}