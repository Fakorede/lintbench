package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

public class UnsafeNativeCodeDetector extends Detector
        implements Detector.OtherFileScanner, Detector.BinaryResourceScanner {

    private static final String[] NATIVE_EXTENSIONS = {
            "so", "elf", "bin", "o", "a", "lo", "dylib", "dll"
    };

    private static final Pattern LIBRARY_DIRECTORY_PATTERN = Pattern.compile(
            "(?:^|[\\\\/])"
                    + "(?:"
                    +   "jniLibs[\\\\/]"
                    +   "|lib[\\\\/](?:armeabi(?:-v7a)?|arm64-v8a|x86(?:_64)?|mips(?:64)?|riscv64)[\\\\/]"
                    +   "|libs[\\\\/](?:armeabi(?:-v7a)?|arm64-v8a|x86(?:_64)?|mips(?:64)?|riscv64)[\\\\/]"
                    + ")");

    private static final String REPORT_MESSAGE =
            "Native code should only be placed in the application's library directory "
                    + "(e.g., jniLibs/... or lib/<abi>/...), not in res/ or assets/.";

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets "
                    + "directories. Placing the code in the library directory provides increased "
                    + "assurance that the code will not be tampered with after application "
                    + "installation. Application developers should use the features of their "
                    + "development environment to place application native libraries into the "
                    + "lib directory of their compiled APKs. Embedding non-shared library native "
                    + "executables into applications should be avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.BINARY_RESOURCE_FILE,
                    Scope.OTHER)
    );

    @Override
    public void run(@NotNull Context context) {
        checkNativeCodeLocation(context);
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        checkNativeCodeLocation(context);
    }

    private void checkNativeCodeLocation(@NotNull Context context) {
        String path = context.getFile().getPath();
        String name = context.getFile().getName();

        if (isNativeCodeFile(name) && !isLibraryDirectory(path)) {
            context.report(
                    ISSUE,
                    Location.create(context.getFile()),
                    REPORT_MESSAGE);
        }
    }

    private static boolean isNativeCodeFile(@NotNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return false;
        }

        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String nativeExtension : NATIVE_EXTENSIONS) {
            if (nativeExtension.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLibraryDirectory(@NotNull String path) {
        return LIBRARY_DIRECTORY_PATTERN.matcher(path).find();
    }
}