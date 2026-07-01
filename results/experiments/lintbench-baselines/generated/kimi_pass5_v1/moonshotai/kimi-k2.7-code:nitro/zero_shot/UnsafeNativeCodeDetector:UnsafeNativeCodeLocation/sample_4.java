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
import java.io.FileInputStream;
import java.io.IOException;

public class UnsafeNativeCodeDetector extends Detector implements Detector.ResourceFolderDetector {

    private static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};
    private static final int ELF_MAGIC_LENGTH = ELF_MAGIC.length;

    private static final Implementation IMPLEMENTATION = new Implementation(
            UnsafeNativeCodeDetector.class,
            Scope.ALL_RESOURCE_FILES_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets "
                    + "directories. Placing the code in the library directory provides "
                    + "increased assurance that the code will not be tampered with after "
                    + "application installation. Application developers should use the "
                    + "features of their development environment to place application native "
                    + "libraries into the lib directory of their compiled APKs. Embedding "
                    + "non-shared library native executables into applications should be "
                    + "avoided when possible.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public void checkFile(@NonNull ResourceContext context, @NonNull File file) {
        if (!file.isFile()) {
            return;
        }

        String path = file.getPath();
        if (path.contains(File.separator + "lib" + File.separator)
                || path.contains(File.separator + "jniLibs" + File.separator)
                || path.contains(File.separator + "libs" + File.separator)) {
            return;
        }

        if (looksLikeNativeCode(file)) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    "Native code should not be placed in the res or assets directories; "
                            + "it should only be placed in the application's library directory");
        }
    }

    private static boolean looksLikeNativeCode(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".so")) {
            return true;
        }

        if (file.length() < ELF_MAGIC_LENGTH) {
            return false;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[ELF_MAGIC_LENGTH];
            int read = in.read(header);
            if (read == ELF_MAGIC_LENGTH) {
                for (int i = 0; i < ELF_MAGIC_LENGTH; i++) {
                    if (header[i] != ELF_MAGIC[i]) {
                        return false;
                    }
                }
                return true;
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }

        return false;
    }
}