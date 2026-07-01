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
import java.io.InputStream;
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

    // OtherFileScanner

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.OTHER);
    }

    @Override
    public void run(Context context) {
        if (isNativeCode(context.file)) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(context.file),
                    "Native code found outside library directory: " + context.file.getName()
            );
        }
    }

    // BinaryResourceScanner

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (isNativeCode(context.file)) {
            context.report(
                    ISSUE,
                    com.android.tools.lint.detector.api.Location.create(context.file),
                    "Native code found outside library directory: " + context.file.getName()
            );
        }
    }

    private static boolean isNativeCode(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            byte[] header = new byte[ELF_MAGIC.length];
            int read = is.read(header);
            if (read < ELF_MAGIC.length) {
                return false;
            }
            return Arrays.equals(header, ELF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }
}