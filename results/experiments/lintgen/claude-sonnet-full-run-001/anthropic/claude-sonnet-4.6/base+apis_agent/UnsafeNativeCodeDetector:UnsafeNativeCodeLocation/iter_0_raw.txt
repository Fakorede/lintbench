package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;

public class UnsafeNativeCodeDetector extends Detector implements OtherFileScanner {

    public static final Issue LOAD = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should " +
            "be avoided when possible.",
            Category.SECURITY,
            4,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    EnumSet.of(Scope.OTHER)));

    // ELF magic bytes: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    public UnsafeNativeCodeDetector() {
    }

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.OTHER);
    }

    @Override
    public void run(Context context) {
        File file = context.file;
        String path = file.getPath();

        // We only care about files that are NOT in the lib/ directory
        // (which is where native libraries should legitimately reside).
        // Check if the file is inside a "lib" directory segment.
        if (isInLibDirectory(file)) {
            return;
        }

        // Check if the file has an ELF header (native binary/library)
        if (isElfFile(file)) {
            String message;
            String name = file.getName();
            if (name.endsWith(".so")) {
                message = "This `.so` file should be in the `lib` directory, not in `" +
                        getRelativePath(context, file) + "`";
            } else {
                message = "Native code file `" + name + "` should not be placed outside " +
                        "the application's library (`lib`) directory";
            }
            context.report(LOAD, Location.create(file), message);
        }
    }

    private static boolean isInLibDirectory(File file) {
        File parent = file.getParentFile();
        while (parent != null) {
            String name = parent.getName();
            if ("lib".equals(name) || "libs".equals(name)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    private static boolean isElfFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 4) {
                return false;
            }
            return Arrays.equals(header, ELF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private static String getRelativePath(Context context, File file) {
        String projectPath = context.getProject().getDir().getPath();
        String filePath = file.getPath();
        if (filePath.startsWith(projectPath)) {
            String relative = filePath.substring(projectPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative;
        }
        return filePath;
    }
}