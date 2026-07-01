package com.android.tools.lint.checks;

import androidx.annotation.NonNull;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;

import java.util.Collections;
import java.util.List;

public class UnsafeNativeCodeDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Dynamically loading code with load()",
            "Dynamically loading code from locations other than the application's library "
                    + "directory or the Android platform's built-in library directories is dangerous, "
                    + "as there is an increased risk that the code could have been tampered with. "
                    + "Applications should use loadLibrary when possible, which provides increased "
                    + "assurance that libraries are loaded from one of these safer locations. "
                    + "Application developers should use the features of their development environment "
                    + "to place application native libraries into the lib directory of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NonNull
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {
        if (context.getEvaluator().isMemberInClass(method, "java.lang.System")
                || context.getEvaluator().isMemberInClass(method, "java.lang.Runtime")) {
            context.report(ISSUE, call, context.getLocation(call),
                    "Use loadLibrary() instead of load() to load native code from a trusted location");
        }
    }
}