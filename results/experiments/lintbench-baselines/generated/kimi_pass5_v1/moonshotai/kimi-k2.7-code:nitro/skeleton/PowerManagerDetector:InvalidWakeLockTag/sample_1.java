package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
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
                    "Wake lock tags passed to PowerManager.newWakeLock must not be null, "
                            + "must not be empty, and must be no longer than 100 characters.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        if (!isPowerManagerNewWakeLock(method)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);

        if (tagArg instanceof ULiteralExpression
                && ((ULiteralExpression) tagArg).getValue() == null) {
            reportInvalidTag(context, tagArg, "Wake lock tag must not be null.");
            return;
        }

        Object value = context.getEvaluator().evaluate(tagArg);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            reportInvalidTag(context, tagArg, "Wake lock tag must not be empty.");
        } else if (tag.length() > 100) {
            reportInvalidTag(context, tagArg,
                    "Wake lock tag must not be longer than 100 characters.");
        }
    }

    private static boolean isPowerManagerNewWakeLock(PsiMethod method) {
        if (method == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return "android.os.PowerManager".equals(containingClass.getQualifiedName());
    }

    private static void reportInvalidTag(
            JavaContext context,
            UExpression node,
            String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}