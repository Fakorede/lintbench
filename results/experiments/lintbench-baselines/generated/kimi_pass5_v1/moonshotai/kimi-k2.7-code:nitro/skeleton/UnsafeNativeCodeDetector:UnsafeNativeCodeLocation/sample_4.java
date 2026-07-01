package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final String MESSAGE = "Native code outside library directory";
    private static final String EXPLANATION =
            "Application native code (shared libraries and executables) should only be placed "
                    + "in the application's library directory (lib/ or jniLibs/). Placing native "
                    + "libraries or executables in res/, assets/, or other locations reduces "
                    + "assurance that the code has not been tampered with after installation. "
                    + "Use System.loadLibrary or Runtime.loadLibrary to load shared libraries, "
                    + "and avoid embedding non-shared library native executables.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    EXPLANATION,
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary", "exec");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String name = method.getName();
        if ("loadLibrary".equals(name)) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if ("load".equals(name)
                && ("java.lang.System".equals(qualifiedName)
                        || "java.lang.Runtime".equals(qualifiedName))) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
        } else if ("exec".equals(name) && "java.lang.Runtime".equals(qualifiedName)) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getMainProject();
        if (project == null) {
            project = context.getProject();
        }
        if (project == null) {
            return;
        }

        File projectDir = project.getDir();
        if (projectDir == null || !projectDir.isDirectory()) {
            return;
        }

        scanProjectForResourceDirs(context, projectDir);
    }

    private void scanProjectForResourceDirs(@NonNull Context context, @NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }
            String name = file.getName();
            if ("res".equals(name) || "assets".equals(name) || "resources".equals(name)) {
                scanNativeFilesInDir(context, file);
            } else if (!"build".equals(name)
                    && !".git".equals(name)
                    && !".gradle".equals(name)) {
                scanProjectForResourceDirs(context, file);
            }
        }
    }

    private void scanNativeFilesInDir(@NonNull Context context, @NonNull File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                scanNativeFilesInDir(context, child);
            }
        } else if (isNativeCodeFile(file)) {
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    private boolean isNativeCodeFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".so")
                || name.endsWith(".elf")
                || name.endsWith(".bin")
                || name.endsWith(".dylib")
                || name.endsWith(".dll")
                || name.endsWith(".a");
    }
}