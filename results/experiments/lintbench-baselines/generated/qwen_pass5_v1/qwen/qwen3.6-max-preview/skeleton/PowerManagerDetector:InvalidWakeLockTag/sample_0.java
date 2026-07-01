package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must not contain '*' or ':' and must be <= 100 characters. " +
                    "Violating these constraints will cause an IllegalArgumentException at runtime.",
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
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object value = context.getEvaluator().evaluate(tagArg);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        String message = null;
        if (tag.length() > 100) {
            message = "Wake lock tag must be <= 100 characters (was " + tag.length() + ")";
        } else if (tag.indexOf(':') != -1) {
            message = "Wake lock tag must not contain ':'";
        } else if (tag.indexOf('*') != -1) {
            message = "Wake lock tag must not contain '*'";
        }

        if (message != null) {
            context.report(ISSUE, tagArg, context.getLocation(tagArg), message);
        }
    }
}