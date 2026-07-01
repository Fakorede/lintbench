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
import org.jetbrains.uast.UCallExpression;
import java.util.Collections;
import java.util.List;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue UNSAFE_CODE =
            Issue.create(
                            "UnsafeDynamicallyLoadedCode",
                            "Unsafe dynamically loaded code",
                            "Dynamically loading code from locations other than the application's "
                                    + "library directory or the Android platform's built-in library directories "
                                    + "is dangerous, as there is an increased risk that the code could have been "
                                    + "tampered with. Applications should use `loadLibrary` when possible, which "
                                    + "provides increased assurance that libraries are loaded from one of these "
                                    + "safer locations.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public UnsafeNativeCodeDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        String methodName = method.getName();
        if (!"load".equals(methodName)) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            if ("java.lang.System".equals(qualifiedName) || "java.lang.Runtime".equals(qualifiedName)) {
                context.report(
                        UNSAFE_CODE,
                        node,
                        context.getLocation(node),
                        "Dynamically loading code using `System.load` is risky. Use `System.loadLibrary` instead when possible.");
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // No-op, implemented as required by specification
    }
}