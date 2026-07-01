package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class UnsafeNativeCodeDetector extends Detector implements Detector.OtherFileScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should be " +
            "avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.OTHER_SCOPE, Scope.RESOURCE_FILE_SCOPE)
    );

    @NotNull
    @Override
    public Collection<String> getApplicableExtensions() {
        return Collections.singletonList("so");
    }

    @Override
    public void visitFile(@NotNull Context context) {
        String path = context.file.getPath().replace('\\', '/');
        if (path.contains("/assets/") || path.contains("/res/") ||
            path.startsWith("assets/") || path.startsWith("res/")) {
            context.report(ISSUE, context.getLocation(context.file),
                    "Native libraries should be placed in the `lib/` directory, not in `assets/` or `res/`.");
        }
    }
}