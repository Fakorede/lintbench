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

    private static final String LOAD_LIBRARY = "loadLibrary";
    private static final String LOAD = "load";

    public UnsafeNativeCodeDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(LOAD_LIBRARY, LOAD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "java.lang.System")
                && !context.getEvaluator().isMemberInClass(method, "java.lang.Runtime")) {
            return;
        }

        String methodName = method.getName();
        if (LOAD.equals(methodName)) {
            // System.load(String filename) or Runtime.load(String filename)
            // Check if the path argument contains unsafe locations
            List<UExpression> arguments = call.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }
            UExpression firstArg = arguments.get(0);
            if (firstArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) firstArg).getValue();
                if (value instanceof String) {
                    String path = (String) value;
                    if (isUnsafeNativeCodePath(path)) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be loaded from an unsafe location. "
                                        + "Ensure that native libraries are placed in the application's "
                                        + "library directory.");
                    }
                }
            }
        } else if (LOAD_LIBRARY.equals(methodName)) {
            // System.loadLibrary(String libname) - generally safe, but check arguments
            // for suspicious paths
            List<UExpression> arguments = call.getValueArguments();
            if (arguments.isEmpty()) {
                return;
            }
            UExpression firstArg = arguments.get(0);
            if (firstArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) firstArg).getValue();
                if (value instanceof String) {
                    String libName = (String) value;
                    // If the library name looks like a path rather than a simple name,
                    // it might be loading from an unsafe location
                    if (libName.contains("/") || libName.contains("\\")) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be loaded from an unsafe location. "
                                        + "Ensure that native libraries are placed in the application's "
                                        + "library directory.");
                    }
                }
            }
        }
    }

    private static boolean isUnsafeNativeCodePath(@NonNull String path) {
        // Check for paths that indicate loading from res or assets directories,
        // or other unsafe locations outside the library directory
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("/res/")
                || lowerPath.contains("/assets/")
                || lowerPath.contains("/data/data/")
                || lowerPath.contains("/sdcard/")
                || lowerPath.contains("/mnt/")
                || lowerPath.contains("/storage/");
    }

    @Override
    public void afterCheckEachProject(@NonNull JavaContext context) {
        // No-op: hook provided for potential future per-project aggregation checks
    }
}