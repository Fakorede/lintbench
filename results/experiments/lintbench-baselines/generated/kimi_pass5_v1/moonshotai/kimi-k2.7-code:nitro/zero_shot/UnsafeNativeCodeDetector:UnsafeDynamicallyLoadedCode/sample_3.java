package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final String CLASS_SYSTEM = "java.lang.System";
    private static final String CLASS_RUNTIME = "java.lang.Runtime";

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Risk of dynamically loaded code",
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
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    @NonNull
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call) {
        PsiMethod method = call.resolve();
        if (method == null) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (CLASS_SYSTEM.equals(qualifiedName) || CLASS_RUNTIME.equals(qualifiedName)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Dynamically loading code using `load` is unsafe; use `loadLibrary` when possible");
        }
    }
}