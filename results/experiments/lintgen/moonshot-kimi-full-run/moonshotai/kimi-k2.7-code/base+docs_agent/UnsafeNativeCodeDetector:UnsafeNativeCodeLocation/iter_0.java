package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class UnsafeNativeCodeDetector extends Detector
        implements ResourceFolderScanner, OtherFileScanner {

    private static final String ID = "UnsafeNativeCodeLocation";
    private static final String DESCRIPTION = "Native code outside library directory";
    private static final String EXPLANATION =
            "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets "
                    + "directories. Placing the code in the library directory provides increased "
                    + "assurance that the code will not be tampered with after application "
                    + "installation. Application developers should use the features of their "
                    + "development environment to place application native libraries into the lib "
                    + "directory of their compiled APKs. Embedding non-shared library native "
                    + "executables into applications should be avoided when possible.";
    private static final Category CATEGORY = Category.SECURITY;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.WARNING;

    private static final Implementation IMPLEMENTATION = new Implementation(
            UnsafeNativeCodeDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE,
            Scope.OTHER_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            CATEGORY,
            PRIORITY,
            SEVERITY,
            IMPLEMENTATION
    );

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.getFolder();
        if (folder == null || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (isNativeExecutable(file)) {
                report(context, file);
            }
        }
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.getFile();
        if (file == null || !file.isFile()) {
            return;
        }

        if (!isInAssetsFolder(file)) {
            return;
        }

        if (isNativeExecutable(file)) {
            report(context, file);
        }
    }

    private static boolean isInAssetsFolder(@NonNull File file) {
        String path = file.getPath();
        String assetsSegment = File.separator + "assets" + File.separator;
        return path.contains(assetsSegment);
    }

    private static boolean isNativeExecutable(@NonNull File file) {
        String name = file.getName();
        String lower = name.toLowerCase();
        if (lower.endsWith(".so") || lower.endsWith(".elf")) {
            return true;
        }

        if (!name.contains(".") && file.isFile()) {
            return isElfFile(file);
        }

        return false;
    }

    private static boolean isElfFile(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            if (fis.read(magic) == 4) {
                return magic[0] == 0x7f
                        && magic[1] == 'E'
                        && magic[2] == 'L'
                        && magic[3] == 'F';
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private void report(@NonNull Context context, @NonNull File file) {
        String message = "Native code should not be placed in the res or assets directories; "
                + "native libraries belong in the lib directory of the APK.";
        Location location = Location.create(file);
        context.report(ISSUE, location, message);
    }
}