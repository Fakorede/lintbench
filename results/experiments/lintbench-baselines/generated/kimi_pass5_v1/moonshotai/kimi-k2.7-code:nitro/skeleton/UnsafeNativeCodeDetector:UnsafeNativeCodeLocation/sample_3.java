package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "Application native code should only be placed in the application's library "
                            + "directory, not in other locations such as the res or assets "
                            + "directories. Placing the code in the library directory provides "
                            + "increased assurance that the code will not be tampered with after "
                            + "application installation. Use System.loadLibrary() or "
                            + "Runtime.loadLibrary() instead of System.load() or Runtime.load() "
                            + "when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String methodName = method.getName();
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
            return;
        }

        // System.loadLibrary / Runtime.loadLibrary always load from the application library
        // directory and are the recommended way to load native code.
        if ("loadLibrary".equals(methodName)) {
            return;
        }

        if ("load".equals(methodName)) {
            List<UExpression> arguments = node.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression argument = arguments.get(0);
            if (isApplicationLibraryPath(argument)) {
                return;
            }

            String message =
                    "Native code is being loaded from a path that is not the application's library "
                            + "directory. This can allow code to be loaded from an insecure or "
                            + "tamperable location. Use System.loadLibrary() or Runtime.loadLibrary() "
                            + "instead.";
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    private boolean isApplicationLibraryPath(UExpression expression) {
        if (expression instanceof UQualifiedReferenceExpression) {
            return isNativeLibraryDirReference((UQualifiedReferenceExpression) expression);
        }

        if (expression instanceof UBinaryExpression) {
            UBinaryExpression binary = (UBinaryExpression) expression;
            return isApplicationLibraryPath(binary.getLeftOperand())
                    || isApplicationLibraryPath(binary.getRightOperand());
        }

        if (expression instanceof UParenthesizedExpression) {
            return isApplicationLibraryPath(
                    ((UParenthesizedExpression) expression).getExpression());
        }

        return false;
    }

    private boolean isNativeLibraryDirReference(UQualifiedReferenceExpression reference) {
        UExpression selector = reference.getSelector();
        if (!(selector instanceof USimpleNameReferenceExpression)) {
            return false;
        }

        String name = ((USimpleNameReferenceExpression) selector).getIdentifier();
        if (!"nativeLibraryDir".equals(name)) {
            return false;
        }

        UExpression receiver = reference.getReceiver();
        if (!(receiver instanceof UCallExpression)) {
            return false;
        }

        return "getApplicationInfo".equals(((UCallExpression) receiver).getMethodName());
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No project-wide resource checks are performed by this source-only detector.
    }
}