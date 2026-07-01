package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.ConstantEvaluator;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
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
                    "Native code loaded from outside the library directory",
                    "Application native code should only be placed in the application's "
                            + "library directory (the lib/ directory of the APK). Loading native "
                            + "libraries with an explicit path, especially from locations such as "
                            + "assets/ or res/, reduces assurance that the code has not been "
                            + "tampered with after installation. Use System.loadLibrary(String) "
                            + "or Runtime.loadLibrary(String) instead, which load from the "
                            + "application's library directory.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
            .setAndroidSpecific(true);

    private final List<Location> mUnsafeLocations = new ArrayList<>();

    public UnsafeNativeCodeDetector() {}

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load", "loadLibrary");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
            return;
        }

        String methodName = method.getName();
        if ("loadLibrary".equals(methodName)) {
            return;
        }

        if ("load".equals(methodName)) {
            if (node.getValueArguments().size() > 0) {
                UExpression argument = node.getValueArguments().get(0);
                String path = ConstantEvaluator.evaluateString(context, argument, false);
                if (path != null && isInLibraryDirectory(path)) {
                    return;
                }
            }
            mUnsafeLocations.add(context.getLocation(node));
        }
    }

    private boolean isInLibraryDirectory(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.contains("/lib/") || lower.startsWith("lib/");
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Location location : mUnsafeLocations) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    location,
                    "Native code should be loaded from the application's library directory; "
                            + "loading native libraries from other locations may be unsafe");
        }
        mUnsafeLocations.clear();
    }
}