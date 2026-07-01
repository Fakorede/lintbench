package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class UnsafeNativeCodeDetector extends Detector implements ResourceFolderDetector {

    private static final byte[] ELF_MAGIC = {(byte) 0x7f, 'E', 'L', 'F'};

    public static final Issue UNSAFE_NATIVE_CODE_LOCATION = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's library directory, not in other locations such as the res or assets directories.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType... folderTypes) {
        return true;
    }

    @Override
    public void checkFile(ResourceContext context, File file) {
        if (file.isDirectory() || !file.exists()) {
            return;
        }

        if (isNativeExecutable(file) && !isInLibraryDirectory(file)) {
            context.report(
                    UNSAFE_NATIVE_CODE_LOCATION,
                    Location.create(file),
                    "Native code should be placed in the application's library directory, not in the res or assets directories."
            );
        }
    }

    private static boolean isNativeExecutable(File file) {
        byte[] header = new byte[ELF_MAGIC.length];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int count = in.read(header);
            if (count < ELF_MAGIC.length) {
                return false;
            }
            return Arrays.equals(header, ELF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isInLibraryDirectory(File file) {
        File parent = file.getParentFile();
        while (parent != null) {
            String name = parent.getName();
            if ("lib".equals(name) || "jniLibs".equals(name)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }
}