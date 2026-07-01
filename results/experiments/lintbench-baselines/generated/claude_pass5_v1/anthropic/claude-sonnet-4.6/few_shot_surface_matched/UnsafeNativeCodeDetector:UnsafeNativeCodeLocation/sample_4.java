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

    private static final String LOAD_LIBRARY = "loadLibrary";
    private static final String LOAD = "load";

    private static final String[] UNSAFE_PATH_PREFIXES = {
        "/res/", "/assets/", "res/", "assets/"
    };

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
        JavaContext.evaluateString(context, call, method);

        // Check that this is a call on System or Runtime
        String className = null;
        PsiMethod resolvedMethod = call.resolve();
        if (resolvedMethod != null) {
            com.intellij.psi.PsiClass containingClass = resolvedMethod.getContainingClass();
            if (containingClass != null) {
                className = containingClass.getQualifiedName();
            }
        }

        if (className == null) {
            return;
        }

        boolean isSystem = "java.lang.System".equals(className);
        boolean isRuntime = "java.lang.Runtime".equals(className);

        if (!isSystem && !isRuntime) {
            return;
        }

        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        if (LOAD.equals(methodName)) {
            // System.load(String filename) or Runtime.load(String filename)
            // Check if the path argument points to a non-library location
            List<UExpression> args = call.getValueArguments();
            if (args.isEmpty()) {
                return;
            }
            UExpression arg = args.get(0);
            Object value = arg.evaluate();
            if (value instanceof String) {
                String path = (String) value;
                for (String prefix : UNSAFE_PATH_PREFIXES) {
                    if (path.contains(prefix)) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be placed in the `res` or `assets` "
                                        + "directories; consider using the library directory instead");
                        return;
                    }
                }
                // Also flag if the path contains "assets" or "res" anywhere
                if (isUnsafePath(path)) {
                    context.report(
                            UNSAFE_NATIVE_CODE_LOCATION,
                            call,
                            context.getLocation(call),
                            "Native code should not be placed in the `res` or `assets` "
                                    + "directories; consider using the library directory instead");
                }
            }
        } else if (LOAD_LIBRARY.equals(methodName)) {
            // System.loadLibrary(String libname) - library name only, generally safe
            // but we can still check for suspicious patterns
            List<UExpression> args = call.getValueArguments();
            if (args.isEmpty()) {
                return;
            }
            UExpression arg = args.get(0);
            Object value = arg.evaluate();
            if (value instanceof String) {
                String libName = (String) value;
                // loadLibrary with path separators is suspicious
                if (libName.contains("/") || libName.contains("\\")) {
                    if (isUnsafePath(libName)) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be placed in the `res` or `assets` "
                                        + "directories; consider using the library directory instead");
                    }
                }
            }
        }
    }

    private static boolean isUnsafePath(@NonNull String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("/res/")
                || lowerPath.contains("/assets/")
                || lowerPath.startsWith("res/")
                || lowerPath.startsWith("assets/");
    }

    @Override
    public void afterCheckEachProject(@NonNull JavaContext context) {
        // No-op: reserved for future project-level aggregation checks
    }
}