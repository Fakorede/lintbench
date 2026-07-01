package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
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
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if ("load".equals(method.getName())) {
            if (context.getEvaluator().isMemberInClass(method, "java.lang.System")
                    || context.getEvaluator().isMemberInClass(method, "java.lang.Runtime")) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Dynamically loading native code via `System.load` can be unsafe if loaded from outside the application's library directory. Prefer `System.loadLibrary`.");
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<java.io.File> assetFolders = context.getProject().getAssetFolders();
        for (java.io.File folder : assetFolders) {
            checkFolder(context, folder);
        }
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File folder : resourceFolders) {
            checkFolder(context, folder);
        }
    }

    private void checkFolder(@NonNull Context context, @NonNull java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] files = file.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    checkFolder(context, f);
                }
            }
        } else {
            String name = file.getName();
            if (name.endsWith(".so") || isElfFile(file)) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "Native code (" + file.getName() + ") should not be placed in the res or assets directories. Use the lib directory instead.");
            }
        }
    }

    private boolean isElfFile(@NonNull java.io.File file) {
        if (!file.isFile() || file.length() < 4) {
            return false;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            if (fis.read(header) == 4) {
                return header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
            }
        } catch (java.io.IOException e) {
            // Ignore potential IO read errors
        }
        return false;
    }
}