package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    private static final String SYSTEM_CLASS = "java.lang.System";
    private static final String RUNTIME_CLASS = "java.lang.Runtime";
    private static final String ASSET_MANAGER_CLASS = "android.content.res.AssetManager";
    private static final String RESOURCES_CLASS = "android.content.res.Resources";

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                            "UnsafeNativeCodeLocation",
                            "Native code loaded from outside the application's library directory",
                            "Application native code should only be placed in the application's "
                                    + "library directory, not in other locations such as the res or "
                                    + "assets directories. Placing the code in the library directory "
                                    + "provides increased assurance that the code will not be tampered "
                                    + "with after application installation. Use System.loadLibrary() "
                                    + "or Runtime.loadLibrary() to load native libraries from the lib "
                                    + "directory.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public UnsafeNativeCodeDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "open", "openFd", "openRawResource", "openRawResourceFd");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        String methodName = method.getName();
        String className =
                method.getContainingClass() != null
                        ? method.getContainingClass().getQualifiedName()
                        : null;

        if ("load".equals(methodName)
                && (SYSTEM_CLASS.equals(className) || RUNTIME_CLASS.equals(className))) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    call,
                    context.getLocation(call),
                    "Loading native code with System.load() or Runtime.load() can load from an "
                            + "arbitrary path. Use System.loadLibrary() or Runtime.loadLibrary() "
                            + "to load native libraries from the application's lib directory.");
        } else if (isAssetOrResourceMethod(className, methodName) && isNativeCodeArgument(call)) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    call,
                    context.getLocation(call),
                    "Native code (.so) should be placed in the application's library directory, "
                            + "not loaded from the assets or resources directories.");
        }
    }

    private boolean isAssetOrResourceMethod(
            @Nullable String className, @NonNull String methodName) {
        if (ASSET_MANAGER_CLASS.equals(className)) {
            return "open".equals(methodName) || "openFd".equals(methodName);
        }
        if (RESOURCES_CLASS.equals(className)) {
            return "openRawResource".equals(methodName) || "openRawResourceFd".equals(methodName);
        }
        return false;
    }

    private boolean isNativeCodeArgument(@NonNull UCallExpression call) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return false;
        }
        Object value = arguments.get(0).evaluate();
        if (value instanceof String) {
            String argument = (String) value;
            return argument.endsWith(".so") || argument.contains(".so");
        }
        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No project-wide aggregation required.
    }
}