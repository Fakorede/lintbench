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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final int MAX_TAG_LENGTH = 127;

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake lock tags must not be null or empty, should be descriptive "
                    + "(for example *MyApp:MyService*), and must not exceed "
                    + MAX_TAG_LENGTH + " characters. See PowerManager documentation.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NotNull JavaContext context,
            @NotNull UCallExpression node,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        if (tagArg instanceof ULiteralExpression) {
            ULiteralExpression literal = (ULiteralExpression) tagArg;
            if (literal.isNull()) {
                reportInvalidTag(context, tagArg, "Wake lock tag must not be null.");
                return;
            }

            Object value = literal.getValue();
            if (value instanceof String) {
                String tag = (String) value;
                if (tag.isEmpty()) {
                    reportInvalidTag(context, tagArg, "Wake lock tag must not be empty.");
                } else if (tag.length() > MAX_TAG_LENGTH) {
                    reportInvalidTag(context, tagArg,
                            "Wake lock tag is too long (" + tag.length()
                                    + " characters); maximum length is "
                                    + MAX_TAG_LENGTH + ".");
                }
            }
        }
    }

    private static void reportInvalidTag(@NotNull JavaContext context,
            @NotNull UExpression node,
            @NotNull String message) {
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}