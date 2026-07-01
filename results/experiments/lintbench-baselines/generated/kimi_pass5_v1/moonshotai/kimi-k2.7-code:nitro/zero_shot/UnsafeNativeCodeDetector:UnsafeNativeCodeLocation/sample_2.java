package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.EnumSet;
import org.jetbrains.annotations.NotNull;

public class UnsafeNativeCodeDetector extends Detector
        implements Detector.BinaryResourceScanner, Detector.OtherFileScanner {

    private static final String NATIVE_EXTENSION = ".so";
    private static final String MESSAGE = "Native code outside library directory";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.OTHER));

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "In general, application native code should only be placed in the application's "
                            + "library directory, not in other locations such as the res or assets "
                            + "directories. Placing the code in the library directory provides increased "
                            + "assurance that the code will not be tampered with after application "
                            + "installation. Application developers should use the features of their "
                            + "development environment to place application native libraries into the lib "
                            + "directory of their compiled APKs. Embedding non-shared library native "
                            + "executables into applications should be avoided when possible.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    @NotNull
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.RESOURCE_FILE, Scope.OTHER);
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType, @NotNull String fileName) {
        return fileName.endsWith(NATIVE_EXTENSION);
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        context.report(ISSUE, Location.Companion.create(context.file), MESSAGE);
    }

    @Override
    public boolean appliesTo(@NotNull Context context, @NotNull File file) {
        if (!file.getName().endsWith(NATIVE_EXTENSION)) {
            return false;
        }
        String path = file.getPath().replace('\\', '/');
        return path.contains("/assets/");
    }

    @Override
    public void run(@NotNull Context context) {
        context.report(ISSUE, Location.Companion.create(context.file), MESSAGE);
    }
}