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
import com.android.tools.lint.detector.api.Location;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "In general, application native code should only be placed in the " +
                    "application's library directory, not in other locations such as the " +
                    "res or assets directories. Placing the code in the library directory " +
                    "provides increased assurance that the code will not be tampered with " +
                    "after application installation. Application developers should use the " +
                    "features of their development environment to place application native " +
                    "libraries into the lib directory of their compiled APKs. Embedding " +
                    "non-shared library native executables into applications should be " +
                    "avoided when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Arrays.asList("load");
    }

    @Override
    public void visitMethodCall(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UCallExpression node,
            @com.android.annotations.NonNull PsiMethod method) {
        String methodName = method.getName();
        if ("load".equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if ("java.lang.System".equals(qualifiedName) || "java.lang.Runtime".equals(qualifiedName)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not load native libraries from arbitrary paths using `System.load()` or `Runtime.load()`. " +
                            "Place native libraries in the standard `lib` directory and load them using `System.loadLibrary()`.");
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
        if (context.getProject() == null) {
            return;
        }
        for (java.io.File assetFolder : context.getProject().getAssetFolders()) {
            checkFolder(context, assetFolder);
        }
        for (java.io.File resFolder : context.getProject().getResourceFolders()) {
            checkFolder(context, resFolder);
        }
    }

    private void checkFolder(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull java.io.File folder) {
        if (!folder.exists()) {
            return;
        }
        java.io.File[] files = folder.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    checkFolder(context, file);
                } else if (file.getName().endsWith(".so")) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            "Native library `" + file.getName() + "` should not be placed in resource or asset directories. " +
                            "Use the standard `lib` directory instead.");
                }
            }
        }
    }
}