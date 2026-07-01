package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
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
                            "In general, application native code should only be placed in the application's "
                                    + "library directory, not in other locations such as the res or assets directories. "
                                    + "Placing the code in the library directory provides increased assurance that the "
                                    + "code will not be tampered with after application installation. Application "
                                    + "developers should use the features of their development environment to place "
                                    + "application native libraries into the lib directory of their compiled APKs. "
                                    + "Embedding non-shared library native executables into applications should "
                                    + "be avoided when possible.",
                            Category.SECURITY,
                            4,
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
        if ("load".equals(methodName)) {
            String evaluator = context.getEvaluator().getMethodClassName(method);
            if ("java.lang.System".equals(evaluator) || "java.lang.Runtime".equals(evaluator)) {
                context.report(
                        UNSAFE_NATIVE_CODE_LOCATION,
                        node,
                        context.getLocation(node),
                        "Using `System.load` or `Runtime.load` to load native libraries is discouraged. "
                                + "Native libraries should be loaded using `System.loadLibrary` from the "
                                + "system library directory.");
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        for (File assetFolder : project.getAssetFolders()) {
            checkDirectoryForNativeCode(context, assetFolder);
        }
        for (File resFolder : project.getResourceFolders()) {
            checkDirectoryForNativeCode(context, resFolder);
        }
    }

    private void checkDirectoryForNativeCode(Context context, File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                checkDirectoryForNativeCode(context, file);
            } else if (file.getName().endsWith(".so")) {
                context.report(
                        UNSAFE_NATIVE_CODE_LOCATION,
                        Location.create(file),
                        "Native code should not be placed in the res or assets directories. "
                                + "Use the lib directory instead.");
            }
        }
    }
}