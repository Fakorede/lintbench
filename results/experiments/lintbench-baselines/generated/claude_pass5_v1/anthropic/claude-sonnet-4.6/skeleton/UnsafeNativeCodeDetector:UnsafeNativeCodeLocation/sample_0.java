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

    // Patterns that indicate native code file extensions
    private static final String[] NATIVE_CODE_EXTENSIONS = {
        ".so", ".dylib", ".dll"
    };

    // Suspicious path patterns that indicate non-library directories
    private static final String[] UNSAFE_PATH_PATTERNS = {
        "res/", "assets/", "/res/", "/assets/"
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

        // Check System.loadLibrary and System.load calls
        if ("loadLibrary".equals(methodName) || "load".equals(methodName)) {
            String containingClass = method.getContainingClass() != null
                    ? method.getContainingClass().getQualifiedName()
                    : null;

            if ("java.lang.System".equals(containingClass)
                    || "java.lang.Runtime".equals(containingClass)) {
                UExpression firstArg = arguments.get(0);
                if (firstArg instanceof ULiteralExpression) {
                    Object value = ((ULiteralExpression) firstArg).getValue();
                    if (value instanceof String) {
                        String path = (String) value;
                        if (isUnsafeNativeCodePath(path)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(firstArg),
                                    "Native code is being loaded from an unsafe location. "
                                            + "Consider using the library directory instead.");
                        }
                    }
                }
            }
            return;
        }

        // Check AssetManager and similar open calls
        if ("open".equals(methodName)
                || "openFd".equals(methodName)
                || "openNonAssetFd".equals(methodName)
                || "openRawResource".equals(methodName)
                || "openRawResourceFd".equals(methodName)) {

            UExpression firstArg = arguments.get(0);
            if (firstArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) firstArg).getValue();
                if (value instanceof String) {
                    String path = (String) value;
                    if (isNativeCodeFile(path)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(firstArg),
                                "Native code is being loaded from an unsafe location "
                                        + "(the assets or res directory). Application native "
                                        + "libraries should be placed in the lib directory.");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No additional checks needed after project scan
    }

    /**
     * Checks if the given path points to a native code file in an unsafe location.
     */
    private static boolean isUnsafeNativeCodePath(@NonNull String path) {
        if (!isNativeCodeFile(path)) {
            return false;
        }
        // Check if the path contains unsafe directory patterns
        String lowerPath = path.toLowerCase();
        for (String unsafePattern : UNSAFE_PATH_PATTERNS) {
            if (lowerPath.contains(unsafePattern.toLowerCase())) {
                return true;
            }
        }
        // Also flag absolute paths that load native code outside lib directory
        if (path.startsWith("/") && !path.contains("/lib/") && !path.contains("/libs/")) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the given path/filename corresponds to a native code file.
     */
    private static boolean isNativeCodeFile(@NonNull String path) {
        String lowerPath = path.toLowerCase();
        for (String ext : NATIVE_CODE_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}