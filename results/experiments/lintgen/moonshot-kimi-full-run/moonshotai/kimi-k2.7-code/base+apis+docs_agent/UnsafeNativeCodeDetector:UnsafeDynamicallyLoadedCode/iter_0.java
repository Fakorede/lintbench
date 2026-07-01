package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final String SYSTEM_CLASS = "java.lang.System";
    private static final String RUNTIME_CLASS = "java.lang.Runtime";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isMemberInClass(method, SYSTEM_CLASS)
                || evaluator.isMemberInClass(method, RUNTIME_CLASS)) {
            context.report(
                    UNSAFE_DYNAMICALLY_LOADED_CODE,
                    call,
                    context.getLocation(call),
                    "Dynamically loading code via `load` is unsafe; use `loadLibrary` instead"
            );
        }
    }

    public static final Issue UNSAFE_DYNAMICALLY_LOADED_CODE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Unsafe dynamic loading of native code",
            "Dynamically loading code from locations other than the application's library "
                    + "directory or the Android platform's built-in library directories is "
                    + "dangerous, as there is an increased risk that the code could have been "
                    + "tampered with. Applications should use `loadLibrary` when possible, which "
                    + "provides increased assurance that libraries are loaded from one of these "
                    + "safer locations. Application developers should use the features of their "
                    + "development environment to place application native libraries into the lib "
                    + "directory of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE)
    );
}