package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeDynamicallyLoadedCode",
                    "Dynamically loaded code could be tampered with",
                    "Dynamically loading code from locations other than the application's library "
                            + "directory or the Android platform's built-in library directories is dangerous, "
                            + "as there is an increased risk that the code could have been tampered with. "
                            + "Applications should use `loadLibrary` when possible, which provides increased "
                            + "assurance that libraries are loaded from one of these safer locations. "
                            + "Application developers should use the features of their development "
                            + "environment to place application native libraries into the lib directory "
                            + "of their compiled APKs.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isMemberInClass(method, "java.lang.System")
                || evaluator.isMemberInClass(method, "java.lang.Runtime")) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Dynamically loading code using `load` is dangerous. Use `loadLibrary` instead.");
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No project-level state to clean up
    }
}