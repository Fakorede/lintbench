package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String SYSTEM = "java.lang.System";
    private static final String RUNTIME = "java.lang.Runtime";

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "Application native code should only be placed in the application's library "
                            + "directory. Loading native code with System.load() or Runtime.load() "
                            + "can load libraries from arbitrary locations such as the res or assets "
                            + "directories. Use System.loadLibrary() or Runtime.loadLibrary() instead, "
                            + "which load shared libraries from the app's lib directory.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public UnsafeNativeCodeDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method == null) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if (!SYSTEM.equals(className) && !RUNTIME.equals(className)) {
            return;
        }

        String methodName = method.getName();
        if ("load".equals(methodName)) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    node,
                    context.getNameLocation(node),
                    "Avoid loading native code with System.load() or Runtime.load(); "
                            + "use System.loadLibrary() or Runtime.loadLibrary() to load from the "
                            + "app's library directory");
        } else if ("loadLibrary".equals(methodName)) {
            List<UExpression> arguments = node.getValueArguments();
            if (!arguments.isEmpty()) {
                String libraryName = context.getEvaluator().evaluateString(arguments.get(0));
                if (libraryName != null
                        && (libraryName.indexOf('/') >= 0 || libraryName.indexOf('\\') >= 0)) {
                    context.report(
                            UNSAFE_NATIVE_CODE_LOCATION,
                            node,
                            context.getNameLocation(node),
                            "The argument to loadLibrary() must be a bare library name, not a path; "
                                    + "paths are loaded from arbitrary locations instead of the "
                                    + "app's library directory");
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // No per-project aggregation is required.
    }
}