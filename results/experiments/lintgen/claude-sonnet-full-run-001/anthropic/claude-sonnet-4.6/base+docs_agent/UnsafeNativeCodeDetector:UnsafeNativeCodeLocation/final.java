package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

public class UnsafeNativeCodeDetector extends Detector implements OtherFileScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
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
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE, Scope.OTHER)
            )
    );

    // ELF magic bytes: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    public UnsafeNativeCodeDetector() {
    }

    // OtherFileScanner - handles assets and other non-resource files

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.OTHER);
    }

    @Override
    public void run(Context context) {
        // Only check files in assets directory (not res, which is handled by checkBinaryResource)
        File file = context.file;
        if (file == null) {
            return;
        }
        String path = file.getPath().replace(File.separatorChar, '/');
        // Only handle assets here; res files are handled by checkBinaryResource
        if (path.contains("/res/")) {
            return;
        }
        if (isNativeCode(file)) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(file),
                    "Native code found outside of the library directory (`lib/`); " +
                    "this code will not be protected from tampering after installation."
            );
        }
    }

    // BinaryResourceScanner - handles res/ files

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (isNativeCode(context.file)) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(context.file),
                    "Native code found outside of the library directory (`lib/`); " +
                    "this code will not be protected from tampering after installation."
            );
        }
    }

    /**
     * Returns true if the given file appears to be a native ELF binary,
     * detected by checking for the ELF magic number at the start of the file.
     */
    private static boolean isNativeCode(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            byte[] header = readBytes(file, ELF_MAGIC.length);
            if (header != null && header.length >= ELF_MAGIC.length) {
                return Arrays.equals(header, ELF_MAGIC);
            }
        } catch (Exception ignored) {
            // If we can't read the file, skip it
        }
        return false;
    }

    private static byte[] readBytes(File file, int count) throws IOException {
        java.io.FileInputStream fis = null;
        try {
            fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[count];
            int read = fis.read(buffer);
            if (read < count) {
                return null;
            }
            return buffer;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }
}