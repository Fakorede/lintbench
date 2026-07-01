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
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

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
    private static final String[] UNSAFE_PATH_PREFIXES = {
        "/res/", "/assets/"
    };

    // Extensions that indicate native code files
    private static final String[] NATIVE_CODE_EXTENSIONS = {
        ".so", ".dex", ".jar", ".apk", ".zip"
    };

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "loadLibrary",
                "load",
                "open",
                "openFd",
                "openNonAssetFd",
                "openRawResource",
                "openRawResourceFd"
        );
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        String methodName = method.getName();
        List<UExpression> arguments = node.getValueArguments();

        if (arguments.isEmpty()) {
            return;
        }

        // Check the first argument for path-based calls
        UExpression firstArg = arguments.get(0);

        if (firstArg instanceof ULiteralExpression) {
            ULiteralExpression literal = (ULiteralExpression) firstArg;
            Object value = literal.getValue();

            if (value instanceof String) {
                String path = (String) value;

                if (isUnsafeNativeCodePath(path)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Native code should not be placed in the `res` or `assets` directories. "
                                    + "Use the `lib` directory instead.");
                }
            }
        }

        // For System.loadLibrary and System.load, check if it's loading from unsafe locations
        if (("load".equals(methodName) || "loadLibrary".equals(methodName))) {
            String containingClass = method.getContainingClass() != null
                    ? method.getContainingClass().getQualifiedName()
                    : null;

            if ("java.lang.System".equals(containingClass)
                    || "java.lang.Runtime".equals(containingClass)) {
                if (firstArg instanceof ULiteralExpression) {
                    ULiteralExpression literal = (ULiteralExpression) firstArg;
                    Object value = literal.getValue();

                    if (value instanceof String) {
                        String path = (String) value;
                        if (isUnsafeNativeCodePath(path)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Native code should not be loaded from the `res` or `assets` "
                                            + "directories. Use the `lib` directory instead.");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op: all checks are done inline during method call visits
    }

    /**
     * Returns true if the given path suggests native code is stored in an unsafe location
     * such as res/ or assets/.
     */
    private static boolean isUnsafeNativeCodePath(@NonNull String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        String lowerPath = path.toLowerCase();

        // Check if path contains unsafe directory prefixes
        boolean inUnsafeDirectory = false;
        for (String prefix : UNSAFE_PATH_PREFIXES) {
            if (lowerPath.contains(prefix) || lowerPath.startsWith(prefix.substring(1))) {
                inUnsafeDirectory = true;
                break;
            }
        }

        if (!inUnsafeDirectory) {
            // Also check if the path starts with "res/" or "assets/" without leading slash
            if (lowerPath.startsWith("res/") || lowerPath.startsWith("assets/")) {
                inUnsafeDirectory = true;
            }
        }

        if (!inUnsafeDirectory) {
            return false;
        }

        // Check if the file has a native code extension
        for (String extension : NATIVE_CODE_EXTENSIONS) {
            if (lowerPath.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }
}