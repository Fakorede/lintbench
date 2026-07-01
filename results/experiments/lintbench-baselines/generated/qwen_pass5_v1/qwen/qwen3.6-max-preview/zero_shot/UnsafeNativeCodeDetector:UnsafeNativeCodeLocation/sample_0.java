package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;

public class UnsafeNativeCodeDetector extends Detector implements OtherFileScanner {

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
        new Implementation(UnsafeNativeCodeDetector.class, EnumSet.of(Scope.OTHER, Scope.BINARY_RESOURCE_FILE))
    );

    @Override
    public void visitFile(Context context, File file) {
        String name = file.getName();
        if (!name.endsWith(".so")) {
            return;
        }

        String path = context.getRelativePath();
        if (path == null) {
            return;
        }

        path = path.replace('\\', '/');

        boolean inLibDir = path.contains("/lib/") || path.contains("/jniLibs/") || path.contains("/libs/") ||
                           path.startsWith("lib/") || path.startsWith("jniLibs/") || path.startsWith("libs/");

        if (!inLibDir) {
            context.report(ISSUE, context.getLocation(file),
                "Native code should be placed in the `lib/` or `jniLibs/` directory, not in `" + path + "`");
        }
    }
}