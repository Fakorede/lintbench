package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.EnumSet;
import org.jetbrains.annotations.NonNull;

public class UnsafeNativeCodeDetector extends Detector implements Detector.OtherFileScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled " +
            "APKs. Embedding non-shared library native executables into applications should " +
            "be avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.OTHER_SCOPE)
    );

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.OTHER_SCOPE);
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        if (!name.endsWith(".so")) {
            return;
        }

        String path = file.getPath().replace('\\', '/');

        // Ignore build, cache, and external native build directories
        if (path.contains("/build/") || path.contains("/.gradle/") || path.contains("/.cxx/")) {
            return;
        }

        boolean inSafeDirectory = path.contains("/jniLibs/") || path.contains("/lib/") ||
                path.startsWith("jniLibs/") || path.startsWith("lib/");

        if (!inSafeDirectory) {
            context.report(ISSUE, Location.create(file),
                    "Native code should be placed in the library directory (`lib/` or `jniLibs/`), " +
                    "not in other locations such as `res/` or `assets/`.");
        }
    }
}