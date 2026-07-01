package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnsafeNativeCodeDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should be " +
            "avoided when possible.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final Set<String> NATIVE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".so", ".dll", ".dylib", ".jnilib"
    ));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        File folder = context.file;
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && isNativeLibrary(file.getName())) {
                context.report(ISSUE, Location.create(file),
                        "Native libraries should be placed in the jniLibs directory, not in res or assets.");
            }
        }
    }

    private static boolean isNativeLibrary(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return NATIVE_EXTENSIONS.contains(fileName.substring(dot).toLowerCase());
        }
        return false;
    }
}