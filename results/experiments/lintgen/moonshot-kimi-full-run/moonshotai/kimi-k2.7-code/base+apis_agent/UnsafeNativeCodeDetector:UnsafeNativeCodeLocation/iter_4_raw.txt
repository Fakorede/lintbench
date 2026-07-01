package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
                    EnumSet.of(Scope.RESOURCE_FOLDER, Scope.OTHER),
                    EnumSet.of(Scope.RESOURCE_FOLDER, Scope.OTHER)));

    private final Set<String> reportedFiles = new HashSet<>();

    @Override
    public void beforeCheckProject(Context context) {
        reportedFiles.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        checkFile(context, context.file);
    }

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.OTHER);
    }

    @Override
    public void run(Context context) {
        checkFile(context, context.file);
    }

    private void checkFile(Context context, File file) {
        if (isInLibDirectory(file, context.getProject())) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    checkFile(context, child);
                }
            }
        } else if (isNativeCode(file)) {
            String path = file.getAbsolutePath();
            if (reportedFiles.add(path)) {
                context.report(ISSUE, Location.create(file), MESSAGE);
            }
        }
    }

    private boolean isInLibDirectory(File file, Project project) {
        if (project == null) {
            return false;
        }
        File libDir = new File(project.getDir(), "lib");
        String libPath = libDir.getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.equals(libPath) || path.startsWith(libPath + File.separator);
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