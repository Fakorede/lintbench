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
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public UnsafeNativeCodeDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        String name = method.getName();
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String className = containingClass.getQualifiedName();
        if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
            return;
        }

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.isEmpty()) {
            return;
        }
        UExpression firstArg = valueArguments.get(0);
        Object evaluated = firstArg.evaluate();
        if (evaluated instanceof String) {
            String path = (String) evaluated;
            if (path.contains("assets/") || path.contains("res/") || path.contains("/sdcard/") 
                    || path.contains("/data/local/") || path.contains("/tmp/")) {
                context.report(
                        UNSAFE_NATIVE_CODE_LOCATION,
                        node,
                        context.getLocation(node),
                        "Shared library should not be loaded from an unsafe location outside the library directory");
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // Required by specification
    }
}