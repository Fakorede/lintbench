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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class UnsafeNativeCodeDetector extends Detector implements OtherFileScanner {

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
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.OTHER_SCOPE
            )
    );

    @Override
    public void afterCheckEachProject(Context context) {
        List<File> assetFolders = context.getProject().getAssetFolders();
        for (File folder : assetFolders) {
            checkFolder(context, folder);
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File folder : resourceFolders) {
            checkFolder(context, folder);
        }
    }

    private void checkFolder(Context context, File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    checkFolder(context, file);
                } else if (isNativeCode(file)) {
                    Location location = Location.create(file);
                    context.report(
                            ISSUE,
                            location,
                            "Shared library or native executable `" + file.getName() + "` found outside of the lib directory"
                    );
                }
            }
        }
    }

    private boolean isNativeCode(File file) {
        if (file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".so")) {
            return true;
        }
        // Check for ELF magic bytes (0x7F 'E' 'L' 'F')
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[4];
            int read = is.read(buffer);
            if (read == 4) {
                return buffer[0] == (byte) 0x7F &&
                       buffer[1] == (byte) 'E' &&
                       buffer[2] == (byte) 'L' &&
                       buffer[3] == (byte) 'F';
            }
        } catch (IOException e) {
            // Ignore read errors
        }
        return false;
    }
}