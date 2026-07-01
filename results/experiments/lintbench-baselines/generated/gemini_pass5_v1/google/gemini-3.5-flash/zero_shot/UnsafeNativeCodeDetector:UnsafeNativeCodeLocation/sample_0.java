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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    EnumSet.of(Scope.OTHER)
            )
    );

    @Override
    public void run(@NonNull Context context) {
        if (!context.getProject().isAndroidProject()) {
            return;
        }

        List<File> assetFolders = context.getProject().getAssetFolders();
        if (assetFolders != null) {
            for (File assetFolder : assetFolders) {
                checkFolder(context, assetFolder);
            }
        }

        List<File> resFolders = context.getProject().getResourceFolders();
        if (resFolders != null) {
            for (File resFolder : resFolders) {
                checkFolder(context, resFolder);
            }
        }
    }

    private void checkFolder(@NonNull Context context, @NonNull File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    checkFolder(context, child);
                }
            }
        } else if (file.isFile()) {
            if (isNativeCode(file)) {
                context.report(
                        ISSUE,
                        Location.create(file),
                        "Native code should not be placed in the res or assets directories. " +
                        "Use the lib directory instead."
                );
            }
        }
    }

    private boolean isNativeCode(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".so")) {
            return true;
        }

        if (file.length() >= 4) {
            try (InputStream is = new FileInputStream(file)) {
                byte[] header = new byte[4];
                if (is.read(header) == 4) {
                    // Check for ELF magic number: 0x7F 'E' 'L' 'F'
                    return header[0] == 0x7f &&
                           header[1] == 'E' &&
                           header[2] == 'L' &&
                           header[3] == 'F';
                }
            } catch (IOException e) {
                // Ignore and fall back to extension check
            }
        }
        return false;
    }
}