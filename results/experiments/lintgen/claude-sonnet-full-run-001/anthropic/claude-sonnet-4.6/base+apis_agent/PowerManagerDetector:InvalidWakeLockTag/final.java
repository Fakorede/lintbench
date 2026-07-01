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
import org.jetbrains.uast.UastLiteralUtils;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming convention `\"<package_name>:<tag>\"` "
                            + "as described in the `PowerManager` documentation. The tag must not be null "
                            + "or empty, and must contain a colon (`:`) separating the package name from "
                            + "the tag name.\n\n"
                            + "See https://developer.android.com/reference/android/os/PowerManager.html "
                            + "for details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/os/PowerManager.html");

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        // newWakeLock(int levelAndFlags, String tag)
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // Only check string literals; we can't evaluate non-literal expressions statically
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!literal.isString()) {
            return;
        }

        Object value = UastLiteralUtils.getValueIfStringLiteral(literal);
        if (value == null) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be null");
            return;
        }

        String tag = (String) value;

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` does not follow the naming convention "
                            + "`\"<package_name>:<tag>\"`; it should contain a colon");
            return;
        }

        // Check that neither the package part nor the tag part is empty
        int colonIndex = tag.indexOf(':');
        String packagePart = tag.substring(0, colonIndex);
        String tagPart = tag.substring(colonIndex + 1);

        if (packagePart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the package name before the colon");
            return;
        }

        if (tagPart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the tag name after the colon");
        }
    }
}