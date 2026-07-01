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
                                    + "application's library directory, not in other locations such "
                                    + "as the res or assets directories. Placing the code in the "
                                    + "library directory provides increased assurance that the code "
                                    + "will not be tampered with after application installation. "
                                    + "Application developers should use the features of their "
                                    + "development environment to place application native libraries "
                                    + "into the lib directory of their compiled APKs. Embedding "
                                    + "non-shared library native executables into applications "
                                    + "should be avoided when possible.",
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
        String methodName = method.getName();

        // Check that this is a call on System or Runtime
        if (!context.getEvaluator().isMemberInClass(method, "java.lang.System")
                && !context.getEvaluator().isMemberInClass(method, "java.lang.Runtime")) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression firstArg = arguments.get(0);

        if (methodName.equals(LOAD)) {
            // load() takes an absolute path - check if it points to res or assets
            if (firstArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) firstArg).getValue();
                if (value instanceof String) {
                    String path = (String) value;
                    if (isUnsafePath(path)) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be placed in the res or assets "
                                        + "directories; consider using a library directory instead");
                    }
                }
            }
        } else if (methodName.equals(LOAD_LIBRARY)) {
            // loadLibrary() loads from the library directory by name - generally safe,
            // but flag if a path separator is included (suggests a non-standard path)
            if (firstArg instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) firstArg).getValue();
                if (value instanceof String) {
                    String libName = (String) value;
                    if (libName.contains("/") || libName.contains("\\")) {
                        context.report(
                                UNSAFE_NATIVE_CODE_LOCATION,
                                call,
                                context.getLocation(call),
                                "Native code should not be placed in the res or assets "
                                        + "directories; consider using a library directory instead");
                    }
                }
            }
        }
    }

    private boolean isUnsafePath(@NonNull String path) {
        for (String prefix : UNSAFE_PATH_PREFIXES) {
            if (path.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull JavaContext context) {
        // No-op: hook provided for potential future per-project aggregation
    }
}