package com.android.tools.lint.checks;

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

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "The tag should be in the format `your.package.name:your.feature.name`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
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

        UExpression tagArg = arguments.get(1);
        Object value = tagArg.evaluate();
        if (!(value instanceof String)) {
            return;
        }
        String tag = (String) value;

        if (tag.isEmpty() || !tag.contains(":")) {
            context.report(ISSUE, node, context.getLocation(tagArg),
                    "Wake lock tag should be in the format `package.name:feature.name`");
        }
    }
}