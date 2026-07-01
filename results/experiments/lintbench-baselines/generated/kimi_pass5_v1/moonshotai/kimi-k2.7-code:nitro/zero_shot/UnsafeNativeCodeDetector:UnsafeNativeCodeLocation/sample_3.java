package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class UnsafeNativeCodeDetector extends ResourceFolderDetector
        implements Detector.OtherFileScanner {

    private static final String MESSAGE = "Native code should live in the app's lib/ directory, not in res/ or assets/";

    private static final Implementation IMPLEMENTATION = new Implementation(
            UnsafeNativeCodeDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE,
            Scope.OTHER_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets "
                    + "directories. Placing the code in the library directory provides increased "
                    + "assurance that the code will not be tampered with after application "
                    + "installation. Application developers should use the features of their "
                    + "development environment to place application native libraries into the lib "
                    + "directory of their compiled APKs. Embedding non-shared library native "
                    + "executables into applications should be avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<ResourceFolderType> getApplicableFolders() {
        return EnumSet.allOf(ResourceFolderType.class);
    }

    @Override
    public void checkFile(ResourceContext context, File file) {
        if (!file.isFile()) {
            return;
        }
        if (isNativeBinary(file)) {
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    @Override
    public Collection<Detector.FileType> getApplicableFiles() {
        return Collections.singletonList(Detector.FileType.BINARY);
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        if (!file.isFile()) {
            return false;
        }
        // Only run on assets files here; the resource-folder scanner covers res/ files.
        String path = file.getAbsolutePath();
        return path.contains(File.separator + "assets" + File.separator);
    }

    @Override
    public void run(Context context) {
        File file = context.getFile();
        if (isNativeBinary(file)) {
            context.report(ISSUE, Location.create(file), MESSAGE);
        }
    }

    private static boolean isNativeBinary(File file) {
        String name = file.getName();
        if (name.endsWith(".so")) {
            return true;
        }

        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read == 4) {
                return header[0] == 0x7f
                        && header[1] == 'E'
                        && header[2] == 'L'
                        && header[3] == 'F';
            }
        } catch (IOException ignored) {
            // Could not inspect the file; treat it as not native code.
        }

        return false;
    }
}