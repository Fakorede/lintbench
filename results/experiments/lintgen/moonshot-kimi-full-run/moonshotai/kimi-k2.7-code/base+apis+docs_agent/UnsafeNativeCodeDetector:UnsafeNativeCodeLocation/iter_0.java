package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class UnsafeNativeCodeDetector extends Detector implements ResourceFolderScanner {

    private static final byte[] ELF_MAGIC = new byte[] {0x7f, 'E', 'L', 'F'};

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "Application native code should only be placed in the application's library directory, "
                    + "not in other locations such as the res or assets directories. Placing the "
                    + "code in the library directory provides increased assurance that the code will "
                    + "not be tampered with after application installation. Developers should use "
                    + "the features of their development environment to place application native "
                    + "libraries into the lib directory of their compiled APKs. Embedding non-shared "
                    + "library native executables into applications should be avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    public void checkFolder(@NotNull ResourceContext context) {
        File[] files = context.getFile().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && isNativeCode(file)) {
                context.report(
                        ISSUE,
                        Location.at(file),
                        "Native code should not be placed in the res directory; place native libraries in the lib directory of the APK"
                );
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        List<File> assetFolders = context.getProject().getAssetFolders();
        for (File folder : assetFolders) {
            scanAssets(folder, context);
        }
    }

    private void scanAssets(@NotNull File folder, @NotNull Context context) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanAssets(file, context);
            } else if (isNativeCode(file)) {
                context.report(
                        ISSUE,
                        Location.at(file),
                        "Native code should not be placed in the assets directory; place native libraries in the lib directory of the APK"
                );
            }
        }
    }

    private boolean isNativeCode(@NotNull File file) {
        String name = file.getName();
        if (name.length() >= 3 && name.regionMatches(true, name.length() - 3, ".so", 0, 3)) {
            return true;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[ELF_MAGIC.length];
            int read = fis.read(header);
            if (read == ELF_MAGIC.length && Arrays.equals(header, ELF_MAGIC)) {
                return true;
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }

        return false;
    }
}