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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags passed to PowerManager.newWakeLock must be non-null "
                            + "and non-empty. Passing a null or empty tag will result in an "
                            + "IllegalArgumentException at runtime, and does not follow the "
                            + "PowerManager naming conventions.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        String tag = ConstantEvaluator.evaluateString(context, tagArg, false);
        if (tag != null) {
            if (tag.isEmpty()) {
                report(context, tagArg, "Wake lock tag must not be empty");
            }
            return;
        }

        if (tagArg instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) tagArg).getValue();
            if (value == null) {
                report(context, tagArg, "Wake lock tag must not be null");
            }
        }
    }

    private void report(JavaContext context, UExpression node, String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}