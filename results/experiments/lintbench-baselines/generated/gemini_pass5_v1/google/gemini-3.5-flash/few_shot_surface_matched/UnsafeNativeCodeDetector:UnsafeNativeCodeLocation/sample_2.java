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
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION =
            Issue.create(
                            "UnsafeNativeCodeLocation",
                            "Native code outside library directory",
                            "In general, application native code should only be placed in the application's "
                                    + "library directory, not in other locations such as the res or assets directories. "
                                    + "Placing the code in the library directory provides increased assurance that the "
                                    + "code will not be tampered with after application installation. Application "
                                    + "developers should use the features of their development environment to place "
                                    + "application native libraries into the lib directory of their compiled "
                                    + "APKs. Embedding non-shared library native executables into applications should "
                                    + "be avoided when possible.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public UnsafeNativeCodeDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        
        String containingClass = method.getContainingClass() != null ? method.getContainingClass().getQualifiedName() : null;
        if ("java.lang.System".equals(containingClass) || "java.lang.Runtime".equals(containingClass)) {
            List<UExpression> valueArguments = node.getValueArguments();
            if (valueArguments.size() == 1) {
                UExpression arg = valueArguments.get(0);
                boolean isUnsafe = true;
                
                if (arg instanceof ULiteralExpression) {
                    Object val = ((ULiteralExpression) arg).getValue();
                    if (val instanceof String) {
                        String path = (String) val;
                        if (!path.contains("assets") && !path.contains("res") && !path.contains("cache") && !path.contains("files")) {
                            isUnsafe = false;
                        }
                    }
                }
                
                if (isUnsafe) {
                    context.report(
                            UNSAFE_NATIVE_CODE_LOCATION,
                            node,
                            context.getLocation(node),
                            "Successfully loading native libraries using `System.load()` or `Runtime.load()` from "
                                    + "locations outside the secure library directory (like assets or res) is unsafe. "
                                    + "Please use `System.loadLibrary()` instead.");
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // Intentionally left blank to meet structural requirements
    }
}