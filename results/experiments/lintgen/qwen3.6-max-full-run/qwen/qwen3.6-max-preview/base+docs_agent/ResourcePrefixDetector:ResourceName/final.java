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
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS, 5, Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FOLDER)));

    @Override
    public void visitFolder(Context context, File folder) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String folderName = folder.getName();
        if (folderName.startsWith("values")) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.startsWith(".")) {
                continue;
            }

            int dotIndex = name.indexOf('.');
            String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;

            if (!baseName.startsWith(prefix)) {
                context.report(ISSUE, Location.create(file),
                        "Resource name `" + baseName + "` does not start with the project prefix `" + prefix + "`");
            }
        }
    }
}