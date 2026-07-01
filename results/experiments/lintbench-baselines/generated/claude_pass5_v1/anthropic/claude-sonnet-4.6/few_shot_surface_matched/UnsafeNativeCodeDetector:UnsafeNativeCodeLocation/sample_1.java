package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                            "UnsafeNativeCodeLocation",
                            "Native code outside library directory",
                            "In general, application native code should only be placed in the "
                                    + "application's library directory, not in other locations such "
                                    + "as the `res` or `assets` directories. Placing the code in "
                                    + "the library directory provides increased assurance that the "
                                    + "code will not be tampered with after application installation. "
                                    + "Application developers should use the features of their "
                                    + "development environment to place application native libraries "
                                    + "into the `lib` directory of their compiled APKs. Embedding "
                                    + "non-shared library native executables into applications should "
                                    + "be avoided when possible.",
                            Category.SECURITY,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private static final String RUNTIME_CLASS = "java.lang.Runtime";
    private static final String SYSTEM_CLASS = "java.lang.System";

    private static final String METHOD_LOAD = "load";
    private static final String METHOD_LOAD_LIBRARY = "loadLibrary";
    private static final String METHOD_EXEC = "exec";

    public UnsafeNativeCodeDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(METHOD_LOAD, METHOD_LOAD_LIBRARY, METHOD_EXEC);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaContext javaContext = context;
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        if (methodName.equals(METHOD_LOAD) || methodName.equals(METHOD_LOAD_LIBRARY)) {
            if (!javaContext.getEvaluator().isMemberInClass(method, RUNTIME_CLASS)
                    && !javaContext.getEvaluator().isMemberInClass(method, SYSTEM_CLASS)) {
                return;
            }

            List<UExpression> arguments = call.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression firstArg = arguments.get(0);
            Object value = firstArg.evaluate();
            if (value instanceof String) {
                String path = (String) value;
                checkNativeCodePath(context, call, path, methodName);
            }

        } else if (methodName.equals(METHOD_EXEC)) {
            if (!javaContext.getEvaluator().isMemberInClass(method, RUNTIME_CLASS)) {
                return;
            }

            List<UExpression> arguments = call.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }

            UExpression firstArg = arguments.get(0);
            Object value = firstArg.evaluate();
            if (value instanceof String) {
                String command = (String) value;
                checkExecCommand(context, call, command);
            }
        }
    }

    private void checkNativeCodePath(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String path,
            @NonNull String methodName) {
        // Check if the path refers to locations outside the library directory
        // such as res/ or assets/ directories
        if (isUnsafePath(path)) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    call,
                    context.getLocation(call),
                    "Native code should not be placed in locations other than the "
                            + "application's library directory");
        }
    }

    private void checkExecCommand(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String command) {
        // Check if the exec command is loading native code from unsafe locations
        if (isUnsafePath(command)) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    call,
                    context.getLocation(call),
                    "Native code should not be placed in locations other than the "
                            + "application's library directory");
        }
    }

    private boolean isUnsafePath(@NonNull String path) {
        // Paths referencing res/ or assets/ directories are considered unsafe
        // for native code placement
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("/res/")
                || lowerPath.contains("/assets/")
                || lowerPath.startsWith("res/")
                || lowerPath.startsWith("assets/")
                || lowerPath.contains("\\res\\")
                || lowerPath.contains("\\assets\\")
                || lowerPath.startsWith("res\\")
                || lowerPath.startsWith("assets\\");
    }

    @Override
    public void afterCheckEachProject(@NonNull JavaContext context) {
        // Hook for any post-project-check processing if needed
    }
}