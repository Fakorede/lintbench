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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final List<String> SAFE_LIBRARY_PREFIXES =
            Arrays.asList(
                    "/system/lib",
                    "/system/vendor/lib",
                    "/vendor/lib",
                    "/system_ext/lib");

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeDynamicallyLoadedCode",
                    "Unsafe dynamically loaded code",
                    "Dynamically loading code from locations other than the application's "
                            + "library directory or the Android platform's built-in library "
                            + "directories is dangerous, as there is an increased risk that the "
                            + "code could have been tampered with. Applications should use "
                            + "`loadLibrary` when possible, which provides increased assurance "
                            + "that libraries are loaded from one of these safer locations. "
                            + "Application developers should use the features of their "
                            + "development environment to place application native libraries "
                            + "into the lib directory of their compiled APKs.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final List<UCallExpression> mPendingReports = new ArrayList<>();

    public UnsafeNativeCodeDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String className = containingClass.getQualifiedName();
        if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        if (!isKnownSafeLoad(args.get(0))) {
            mPendingReports.add(node);
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!(context instanceof JavaContext) || mPendingReports.isEmpty()) {
            mPendingReports.clear();
            return;
        }
        JavaContext javaContext = (JavaContext) context;
        for (UCallExpression node : mPendingReports) {
            javaContext.report(
                    ISSUE,
                    node,
                    javaContext.getLocation(node),
                    "Dynamically loading code via `load` is unsafe; use `loadLibrary` with a "
                            + "library placed in the application's lib directory instead.");
        }
        mPendingReports.clear();
    }

    private static boolean isKnownSafeLoad(@NonNull UExpression argument) {
        if (argument instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) argument).getValue();
            if (value instanceof String) {
                String path = (String) value;
                for (String prefix : SAFE_LIBRARY_PREFIXES) {
                    if (path.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}