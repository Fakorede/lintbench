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

    private static final String SYSTEM_CLASS = "java.lang.System";
    private static final String RUNTIME_CLASS = "java.lang.Runtime";
    private static final String LOAD_METHOD = "load";

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "Loading native code from a path outside the application's library directory "
                            + "(for example, from the assets or res directories) reduces the "
                            + "assurance that the code has not been tampered with after "
                            + "installation. Use System.loadLibrary(...) or ensure native "
                            + "libraries are placed under the lib directory of the APK.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(LOAD_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!isNativeLoadCall(method)) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        Object value = arguments.get(0).evaluate();
        if (!(value instanceof String)) {
            return;
        }

        String path = (String) value;
        if (path == null || path.isEmpty()) {
            return;
        }

        if (!isInApplicationLibraryDirectory(path)) {
            String message =
                    "Loading native code from \""
                            + path
                            + "\" which is outside the application's library directory";
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    private static boolean isNativeLoadCall(@NonNull PsiMethod method) {
        if (!LOAD_METHOD.equals(method.getName())) {
            return false;
        }

        if (method.getContainingClass() == null) {
            return false;
        }

        String className = method.getContainingClass().getQualifiedName();
        return SYSTEM_CLASS.equals(className) || RUNTIME_CLASS.equals(className);
    }

    private static boolean isInApplicationLibraryDirectory(@NonNull String path) {
        String lower = path.toLowerCase();

        // Known safe application and system library directories.
        if ((path.startsWith("/data/data/") || path.startsWith("/data/app/"))
                && lower.contains("/lib/")) {
            return true;
        }
        if ((path.startsWith("/system/") || path.startsWith("/vendor/"))
                && lower.contains("/lib/")) {
            return true;
        }

        // Explicitly known unsafe locations.
        if (lower.contains("/assets/") || lower.contains("/res/")) {
            return false;
        }
        if (lower.startsWith("/sdcard/")
                || lower.startsWith("/mnt/")
                || lower.startsWith("/storage/")
                || lower.startsWith("content://")
                || lower.startsWith("file:///sdcard")
                || lower.startsWith("file:///android_asset")
                || lower.startsWith("file:///android_res")) {
            return false;
        }

        // Any other explicit path is treated as outside the application lib directory.
        return false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No project-wide aggregation is required for this source-level check.
    }
}