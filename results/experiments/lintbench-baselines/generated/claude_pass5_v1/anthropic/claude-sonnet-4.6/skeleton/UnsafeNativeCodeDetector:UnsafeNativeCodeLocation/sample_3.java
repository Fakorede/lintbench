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
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "In general, application native code should only be placed in the application's "
                            + "library directory, not in other locations such as the `res` or `assets` "
                            + "directories. Placing the code in the library directory provides increased "
                            + "assurance that the code will not be tampered with after application "
                            + "installation. Application developers should use the features of their "
                            + "development environment to place application native libraries into the "
                            + "`lib` directory of their compiled APKs. Embedding non-shared library "
                            + "native executables into applications should be avoided when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Patterns that indicate unsafe native code locations
    private static final String[] UNSAFE_PATTERNS = {
        "/res/", "/assets/"
    };

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "load",
                "loadLibrary",
                "exec",
                "getResourceAsStream",
                "open"
        );
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        String methodName = method.getName();
        String containingClassName = method.getContainingClass() != null
                ? method.getContainingClass().getQualifiedName()
                : "";

        if (containingClassName == null) {
            containingClassName = "";
        }

        // Check System.load() and Runtime.load() - loading native code from explicit paths
        if (methodName.equals("load")) {
            if (containingClassName.equals("java.lang.System")
                    || containingClassName.equals("java.lang.Runtime")) {
                List<UExpression> arguments = node.getValueArguments();
                if (!arguments.isEmpty()) {
                    UExpression arg = arguments.get(0);
                    String value = getStringValue(arg);
                    if (value != null && isUnsafeNativeCodeLocation(value)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Native code should not be loaded from an unsafe location. "
                                        + "It should be placed in the application's library directory.");
                    }
                }
            }
        }

        // Check Runtime.exec() - executing native code
        if (methodName.equals("exec")) {
            if (containingClassName.equals("java.lang.Runtime")) {
                List<UExpression> arguments = node.getValueArguments();
                if (!arguments.isEmpty()) {
                    UExpression arg = arguments.get(0);
                    String value = getStringValue(arg);
                    if (value != null && isUnsafeNativeCodeLocation(value)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Executing native code from an unsafe location. "
                                        + "Native executables should not be placed in res or assets directories.");
                    }
                }
            }
        }

        // Check AssetManager.open() - opening assets that might be native code
        if (methodName.equals("open")) {
            if (containingClassName.equals("android.content.res.AssetManager")) {
                List<UExpression> arguments = node.getValueArguments();
                if (!arguments.isEmpty()) {
                    UExpression arg = arguments.get(0);
                    String value = getStringValue(arg);
                    if (value != null && isNativeFile(value)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Native code (`.so` files) should not be placed in the assets directory. "
                                        + "It should be placed in the application's library directory.");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No additional checks needed after each project
    }

    /**
     * Checks if the given path refers to an unsafe native code location
     * (i.e., res/ or assets/ directories).
     */
    private static boolean isUnsafeNativeCodeLocation(@NonNull String path) {
        if (!isNativeFile(path)) {
            return false;
        }
        for (String pattern : UNSAFE_PATTERNS) {
            if (path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given filename/path refers to a native file (.so or executable).
     */
    private static boolean isNativeFile(@NonNull String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".so")
                || lowerPath.endsWith(".so.1")
                || lowerPath.contains(".so.");
    }

    /**
     * Attempts to extract a string constant value from a UAST expression.
     */
    private static String getStringValue(@NonNull UExpression expression) {
        Object value = expression.evaluate();
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}