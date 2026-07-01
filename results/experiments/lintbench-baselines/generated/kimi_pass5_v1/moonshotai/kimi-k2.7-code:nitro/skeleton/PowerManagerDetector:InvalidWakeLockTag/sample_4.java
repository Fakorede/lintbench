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

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags passed to PowerManager.newWakeLock must be non-empty and "
                            + "must not exceed 127 characters in length.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String CLASS_POWER_MANAGER = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 127;

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        if (method.getContainingClass() == null) {
            return;
        }

        if (!CLASS_POWER_MANAGER.equals(method.getContainingClass().getQualifiedName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object value = ConstantEvaluator.evaluate(context, tagArg);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tags must not be empty");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            context.report(
                    ISSUE,
                    tagArg,
                    context.getLocation(tagArg),
                    "Wake lock tag length (" + tag.length()
                            + ") exceeds the maximum allowed length of " + MAX_TAG_LENGTH);
        }
    }
}