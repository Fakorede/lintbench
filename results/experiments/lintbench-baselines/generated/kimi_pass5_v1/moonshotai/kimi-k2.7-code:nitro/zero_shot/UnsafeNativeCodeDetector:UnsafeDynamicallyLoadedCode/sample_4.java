package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements Detector.UastScanner {

    private static final String SYSTEM_CLASS = "java.lang.System";
    private static final String RUNTIME_CLASS = "java.lang.Runtime";
    private static final String LOAD_METHOD = "load";

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Dynamically loaded native code",
            "Dynamically loading code using `load` from locations other than the application's "
                    + "library directory or the Android platform's built-in library directories "
                    + "is dangerous, as there is an increased risk that the code could have been "
                    + "tampered with. Applications should use `loadLibrary` when possible, which "
                    + "loads libraries from one of these safer locations. Application developers "
                    + "should use the features of their development environment to place "
                    + "application native libraries into the lib directory of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(LOAD_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isMemberInClass(method, SYSTEM_CLASS)
                || evaluator.isMemberInClass(method, RUNTIME_CLASS)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Do not dynamically load code with `load`; use `loadLibrary` instead.");
        }
    }
}