package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Locale;

public class UnsafeNativeCodeDetector extends Detector implements Detector.BinaryResourceScanner {

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
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.BINARY_RESOURCE_FILE));

    @Override
    public boolean appliesTo(@NonNull ResourceContext context, @NonNull File file) {
        return isNativeCodeFile(file);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String path = file.getPath();
        String name = file.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".so") && isInLibDirectory(path)) {
            return;
        }

        String message = name.endsWith(".so")
                ? "Native code (.so) should be placed in the app's library directory (lib/), not in res or assets"
                : "Embedding non-shared library native executables should be avoided when possible";

        context.report(ISSUE, Location.create(file), message);
    }

    private static boolean isNativeCodeFile(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".so")
                || name.endsWith(".dll")
                || name.endsWith(".dylib")
                || name.endsWith(".exe")
                || name.endsWith(".bin");
    }

    private static boolean isInLibDirectory(@NonNull String path) {
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (segment.equals("lib") || segment.equals("jniLibs")) {
                return true;
            }
        }
        return false;
    }
}