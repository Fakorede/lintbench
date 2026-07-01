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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                            "UnsafeNativeCodeLocation",
                            "Native code outside library directory",
                            "In general, application native code should only be placed in the "
                                    + "application's library directory, not in other locations such as the "
                                    + "res or assets directories. Placing the code in the library directory "
                                    + "provides increased assurance that the code will not be tampered with "
                                    + "after application installation. Application developers should use the "
                                    + "features of their development environment to place application native "
                                    + "libraries into the lib directory of their compiled APKs. Embedding "
                                    + "non-shared library native executables into applications should be "
                                    + "avoided when possible.",
                            Category.SECURITY,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

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
        String name = method.getName();
        if ("load".equals(name)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if ("java.lang.System".equals(qualifiedName) || "java.lang.Runtime".equals(qualifiedName)) {
                    context.report(
                            UNSAFE_NATIVE_CODE_LOCATION,
                            node,
                            context.getLocation(node),
                            "Do not load native libraries using absolute paths with `System.load()` or `Runtime.load()`. "
                                    + "Use `System.loadLibrary()` to load libraries safely from the standard native library directory.");
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op standard implementation
    }
}