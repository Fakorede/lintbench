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
                            + "library directory, not in other locations such as the res or assets directories. "
                            + "Placing the code in the library directory provides increased assurance that the "
                            + "code will not be tampered with after application installation. Application "
                            + "developers should use the features of their development environment to place "
                            + "application native libraries into the lib directory of their compiled "
                            + "APKs. Embedding non-shared library native executables into applications should "
                            + "be avoided when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Method names that load native libraries or execute native code
    private static final String LOAD = "load";
    private static final String LOAD_LIBRARY = "loadLibrary";

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(LOAD, LOAD_LIBRARY);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        // Check if the method is called on System or Runtime
        if (!context.getEvaluator().isMemberInClass(method, "java.lang.System")
                && !context.getEvaluator().isMemberInClass(method, "java.lang.Runtime")) {
            return;
        }

        String methodName = method.getName();

        if (LOAD.equals(methodName)) {
            // System.load() or Runtime.load() takes an absolute path
            // Check if the path contains suspicious locations (not in lib directory)
            List<UExpression> arguments = node.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression pathArg = arguments.get(0);
            Object value = pathArg.evaluate();
            if (value instanceof String) {
                String path = (String) value;
                // Flag if path contains assets, res, or other non-lib locations
                if (isUnsafeNativeCodePath(path)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Unsafe native code loading: native libraries should be loaded from "
                                    + "the application's library directory, not from `"
                                    + path + "`");
                }
            }
        } else if (LOAD_LIBRARY.equals(methodName)) {
            // System.loadLibrary() loads from the lib directory - this is generally safe
            // but we can still check for suspicious patterns
            List<UExpression> arguments = node.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression libraryArg = arguments.get(0);
            Object value = libraryArg.evaluate();
            if (value instanceof String) {
                String libraryName = (String) value;
                // Check if the library name contains path separators or suspicious patterns
                if (libraryName.contains("/") || libraryName.contains("\\")) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Unsafe native code loading: library name should not contain path "
                                    + "separators. Use `System.load()` with an absolute path "
                                    + "or `System.loadLibrary()` with just the library name.");
                }
            }
        }
    }

    private static boolean isUnsafeNativeCodePath(@NonNull String path) {
        // Normalize path separators
        String normalizedPath = path.replace('\\', '/').toLowerCase();

        // Check for suspicious locations
        if (normalizedPath.contains("/assets/")
                || normalizedPath.contains("/res/")
                || normalizedPath.contains("/cache/")
                || normalizedPath.contains("/sdcard/")
                || normalizedPath.contains("/external_sd/")
                || normalizedPath.contains("/mnt/")
                || normalizedPath.contains("/data/data/") && !normalizedPath.contains("/lib/")) {
            return true;
        }

        // Check if it ends with a native library extension but not in a lib directory
        if ((normalizedPath.endsWith(".so")
                || normalizedPath.endsWith(".dll")
                || normalizedPath.endsWith(".dylib"))
                && !normalizedPath.contains("/lib/")
                && !normalizedPath.contains("/libs/")) {
            return true;
        }

        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing specific to do after checking each project
        // The individual method call checks handle all reporting
    }
}