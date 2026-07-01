package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.EnumSet;

public class UnsafeNativeCodeDetector extends Detector {

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
            new Implementation(UnsafeNativeCodeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.RESOURCE_FILE_SCOPE;
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".so")) {
            ResourceFolderType folderType = context.getResourceFolderType(file);
            if (folderType != null) {
                context.report(ISSUE, context.getLocation(file),
                        "Native libraries should be placed in the `lib/` or `jniLibs/` directory, " +
                        "not in `res/` or `assets/`.");
            }
        }
    }
}