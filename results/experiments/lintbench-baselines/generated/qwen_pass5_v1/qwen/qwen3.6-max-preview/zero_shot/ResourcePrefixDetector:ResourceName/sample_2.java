package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableFolderNames() {
        return Arrays.asList("layout", "drawable", "mipmap", "anim", "animator",
                "color", "interpolator", "menu", "navigation", "raw", "transition", "xml", "font");
    }

    @Override
    public void visitFolder(@NotNull Context context, @NotNull ResourceFolderType folderType, @NotNull List<File> files) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            int dot = name.indexOf('.');
            String baseName = dot != -1 ? name.substring(0, dot) : name;

            if (!baseName.startsWith(prefix)) {
                Location location = Location.create(file);
                context.report(ISSUE, location, "Resource name must start with prefix `" + prefix + "`");
            }
        }
    }
}