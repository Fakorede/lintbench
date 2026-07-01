package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

public class UnsafeNativeCodeDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets directories. "
                    + "Placing the code in the library directory provides increased assurance that the "
                    + "code will not be tampered with after application installation. Application "
                    + "developers should use the features of their development environment to place "
                    + "application native libraries into the lib directory of their compiled "
                    + "APKs. Embedding non-shared library native executables into applications should "
                    + "be avoided when possible.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.OTHER_SCOPE
            )
    );

    @Override
    public void run(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        for (File assetFolder : context.getProject().getAssetFolders()) {
            checkFolder(context, assetFolder);
        }

        for (File resFolder : context.getProject().getResourceFolders()) {
            checkFolder(context, resFolder);
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
                        "Native code should not be placed in the res or assets directories. "
                                + "Place it in the jniLibs directory instead."
                );
            }
        }
    }

    private boolean isNativeCode(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".so")) {
            return true;
        }
        if (file.length() >= 4) {
            try (InputStream is = new FileInputStream(file)) {
                byte[] header = new byte[4];
                if (is.read(header) == 4) {
                    return header[0] == 0x7F
                            && header[1] == 'E'
                            && header[2] == 'L'
                            && header[3] == 'F';
                }
            } catch (IOException e) {
                // Ignore and fall back
            }
        }
        return false;
    }
}