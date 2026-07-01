package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String CLASS_SYSTEM = "java.lang.System";
    private static final String CLASS_RUNTIME = "java.lang.Runtime";

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
                                    + "development environment to place application native "
                                    + "libraries into the lib directory of their compiled APKs. "
                                    + "Embedding non-shared library native executables into "
                                    + "applications should be avoided when possible.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

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
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if (!CLASS_SYSTEM.equals(className) && !CLASS_RUNTIME.equals(className)) {
            return;
        }

        String methodName = method.getName();
        // System.loadLibrary / Runtime.loadLibrary load from the application's lib directory.
        if ("loadLibrary".equals(methodName)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() != 1) {
            return;
        }

        UExpression argument = arguments.get(0);
        String path = ConstantEvaluator.evaluateString(context, argument, false);
        if (path != null) {
            if (isInLibraryDirectory(path)) {
                return;
            }
            reportUnsafe(
                    context,
                    call,
                    "Loading native code from `" + path + "` may load code outside the "
                            + "application's library directory.");
        } else {
            if (referencesLibraryDirectory(argument)) {
                return;
            }
            reportUnsafe(
                    context,
                    call,
                    "The path used to load native code could not be verified to be inside the "
                            + "application's library directory.");
        }
    }

    private static boolean isInLibraryDirectory(@NonNull String path) {
        // Native libraries are extracted to the application's nativeLibraryDir (e.g. .../lib/).
        return path.contains("nativeLibraryDir") || path.contains("/lib/");
    }

    private static boolean referencesLibraryDirectory(@NonNull UExpression expression) {
        String source = expression.asSourceString();
        return source != null && source.contains("nativeLibraryDir");
    }

    private static void reportUnsafe(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String message) {
        context.report(
                UNSAFE_NATIVE_CODE_LOCATION,
                call,
                context.getLocation(call),
                message);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No project-wide aggregation is required for this check.
    }
}