package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;

public class UnsafeNativeCodeDetector extends Detector
        implements Detector.ResourceFolderScanner, Detector.OtherFileScanner {

    private static final String EXPLANATION =
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the `res` or `assets` directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the `lib` directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should be " +
            "avoided when possible.";

    private static final String MESSAGE =
            "Native code should not be embedded outside the application's lib directory";

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            EXPLANATION,
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE,
                    Scope.ALL_OTHER_FILES_SCOPE));

    @Override
    public Collection<ResourceFolderType> getApplicableFolders() {
        return EnumSet.allOf(ResourceFolderType.class);
    }

    @Override
    public void checkFolder(ResourceContext context) {
        File folder = context.getFile();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                checkFile(context, file);
            }
        }
    }

    private void checkFile(Context context, File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    checkFile(context, child);
                }
            }
        } else if (isNativeCode(file)) {
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return !file.isDirectory();
    }

    @Override
    public void run(Context context) {
        File file = context.getFile();
        if (isNativeCode(file)) {
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    private boolean isNativeCode(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".so") || name.endsWith(".elf")) {
            return true;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read == 4) {
                return header[0] == 0x7f
                        && header[1] == 'E'
                        && header[2] == 'L'
                        && header[3] == 'F';
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }

        return false;
    }
}