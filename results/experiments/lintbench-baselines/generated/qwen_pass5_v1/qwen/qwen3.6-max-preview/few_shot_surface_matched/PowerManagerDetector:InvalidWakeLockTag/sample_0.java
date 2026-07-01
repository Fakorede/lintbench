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

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the "
                            + "`PowerManager` documentation. The tag should be of the form "
                            + "`\"your_package_name:your_tag\"`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArg = arguments.get(1);
        if (!(tagArg instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArg;
        if (!literal.isStringLiteral()) {
            return;
        }

        Object value = literal.getValue();
        if (value instanceof String) {
            String tag = (String) value;
            if (!tag.contains(":")) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must contain a colon (`:`) separator, e.g., `\"package:tag\"`");
            }
        }
    }
}