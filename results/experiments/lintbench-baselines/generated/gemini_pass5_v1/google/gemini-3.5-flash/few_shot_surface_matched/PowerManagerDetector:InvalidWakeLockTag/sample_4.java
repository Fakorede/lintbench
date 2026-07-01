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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. They should be under 50 characters, "
                                    + "contain no spaces, and use a prefix with a colon (e.g. 'myapp:mytag') "
                                    + "to avoid collisions.",
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
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object value = tagArg.evaluate();
        if (value instanceof String) {
            String tag = (String) value;
            if (tag.isEmpty()) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag cannot be empty");
            } else if (tag.length() > 50) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag is too long (should be under 50 characters)");
            } else if (tag.contains(" ")) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag should not contain spaces");
            } else if (!tag.contains(":")) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag should use a unique prefix (such as a package name) "
                                + "followed by a colon to avoid collisions (e.g. 'myapp:mytag')");
            }
        }
    }
}