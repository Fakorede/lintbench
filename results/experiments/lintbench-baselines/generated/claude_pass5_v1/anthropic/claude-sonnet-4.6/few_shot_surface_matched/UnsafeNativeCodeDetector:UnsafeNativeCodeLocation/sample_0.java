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

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                            "UnsafeNativeCodeLocation",
                            "Native code outside library directory",
                            "In general, application native code should only be placed in the "
                                    + "application's library directory, not in other locations such as "
                                    + "the `res` or `assets` directories. Placing the code in the library "
                                    + "directory provides increased assurance that the code will not be "
                                    + "tampered with after application installation. Application developers "
                                    + "should use the features of their development environment to place "
                                    + "application native libraries into the `lib` directory of their "
                                    + "compiled APKs. Embedding non-shared library native executables into "
                                    + "applications should be avoided when possible.",
                            Category.SECURITY,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private static final String RUNTIME_CLASS = "java.lang.Runtime";
    private static final String SYSTEM_CLASS = "java.lang.System";

    public UnsafeNativeCodeDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        String methodName = method.getName();
        JavaContext javaContext = context;

        // Check that this is a call on Runtime or System
        if (!context.getEvaluator().isMemberInClass(method, RUNTIME_CLASS)
                && !context.getEvaluator().isMemberInClass(method, SYSTEM_CLASS)) {
            return;
        }

        if ("load".equals(methodName)) {
            // load(String filename) - loads a native library by absolute path
            // Check if the path argument refers to a non-library location
            List<UExpression> arguments = call.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }
            UExpression firstArg = arguments.get(0);
            Object value = firstArg.evaluate();
            if (value instanceof String) {
                String path = (String) value;
                if (isUnsafeNativePath(path)) {
                    context.report(
                            UNSAFE_NATIVE_CODE_LOCATION,
                            call,
                            context.getLocation(call),
                            "Native code should not be placed in locations other than the "
                                    + "application's library directory");
                }
            } else {
                // If we can't evaluate the path statically, flag the use of load()
                // since it loads arbitrary paths which is inherently risky
                context.report(
                        UNSAFE_NATIVE_CODE_LOCATION,
                        call,
                        context.getLocation(call),
                        "Native code should not be placed in locations other than the "
                                + "application's library directory");
            }
        } else if ("loadLibrary".equals(methodName)) {
            // loadLibrary(String libname) - loads a named library
            // This is the safe/recommended approach, no need to warn
        }
    }

    private static boolean isUnsafeNativePath(@NonNull String path) {
        // Paths that suggest the native code is stored in res/ or assets/
        // or other non-library locations
        if (path.contains("/res/") || path.contains("/assets/")) {
            return true;
        }
        // Check for paths that do not go through the standard lib directory
        if (!path.contains("/lib/") && !path.contains("/libs/")) {
            return true;
        }
        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull JavaContext context) {
        // No-op: post-project checks not needed for this detector
    }
}