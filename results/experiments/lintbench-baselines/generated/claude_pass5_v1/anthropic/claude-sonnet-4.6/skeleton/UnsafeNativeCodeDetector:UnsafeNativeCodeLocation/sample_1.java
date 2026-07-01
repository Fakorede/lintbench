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
                    "In general, application native code should only be placed in the "
                            + "application's library directory, not in other locations such as "
                            + "the res or assets directories. Placing the code in the library "
                            + "directory provides increased assurance that the code will not be "
                            + "tampered with after application installation. Application "
                            + "developers should use the features of their development environment "
                            + "to place application native libraries into the lib directory of "
                            + "their compiled APKs. Embedding non-shared library native "
                            + "executables into applications should be avoided when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Patterns that indicate native code in unsafe locations
    private static final String[] UNSAFE_NATIVE_PATTERNS = {
        "/assets/", "/res/"
    };

    // Native library file extensions
    private static final String[] NATIVE_EXTENSIONS = {
        ".so", ".dylib", ".dll"
    };

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "load",       // System.load() or Runtime.load()
                "loadLibrary" // System.loadLibrary() or Runtime.loadLibrary()
        );
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        // Check if this is a call on System or Runtime
        String className = method.getContainingClass() != null
                ? method.getContainingClass().getQualifiedName()
                : null;

        if (className == null) {
            return;
        }

        boolean isSystemOrRuntime = "java.lang.System".equals(className)
                || "java.lang.Runtime".equals(className);

        if (!isSystemOrRuntime) {
            return;
        }

        String methodName = method.getName();

        // For load(), check if the path argument points to an unsafe location
        if ("load".equals(methodName)) {
            List<UExpression> arguments = node.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression pathArg = arguments.get(0);
            if (pathArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) pathArg).getValue();
                if (value instanceof String) {
                    String path = (String) value;
                    if (isUnsafeNativePath(path)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Native code is being loaded from an unsafe location. "
                                        + "Native libraries should only be loaded from the "
                                        + "application's library directory.");
                    }
                }
            } else {
                // Even if we can't evaluate the argument statically, flag the use of
                // System.load() with a non-literal as potentially unsafe
                String evaluatedValue = UastLiteralUtils.getValueIfStringLiteral(pathArg);
                if (evaluatedValue != null && isUnsafeNativePath(evaluatedValue)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Native code is being loaded from an unsafe location. "
                                    + "Native libraries should only be loaded from the "
                                    + "application's library directory.");
                }
            }
        }
        // loadLibrary() loads from the library directory by name, which is generally safe
        // but we can still check for suspicious names if needed
    }

    /**
     * Returns true if the given path suggests the native code is stored in an unsafe location
     * (e.g., assets or res directories).
     */
    private static boolean isUnsafeNativePath(@NonNull String path) {
        String lowerPath = path.toLowerCase();

        // Check if the path contains unsafe directory patterns
        for (String pattern : UNSAFE_NATIVE_PATTERNS) {
            if (lowerPath.contains(pattern)) {
                return true;
            }
        }

        // Also check if it ends with a native extension and is not in a lib directory
        boolean hasNativeExtension = false;
        for (String ext : NATIVE_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                hasNativeExtension = true;
                break;
            }
        }

        if (hasNativeExtension) {
            // If the path doesn't go through a lib/ directory, it might be unsafe
            if (!lowerPath.contains("/lib/") && !lowerPath.contains("/libs/")) {
                // Only flag if it seems to be an explicit path (contains directory separators)
                // and doesn't look like a system path
                if (path.contains("/") && !path.startsWith("/system/")
                        && !path.startsWith("/vendor/")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No additional checks needed after each project
    }
}