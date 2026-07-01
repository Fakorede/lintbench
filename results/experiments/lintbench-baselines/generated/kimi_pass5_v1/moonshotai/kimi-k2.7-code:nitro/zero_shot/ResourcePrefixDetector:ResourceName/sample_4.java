package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends ResourceXmlDetector
        implements Detector.ResourceFolderScanner {

    private static final String RESOURCE_PREFIX = "resourcePrefix";

    private static final Implementation IMPLEMENTATION = new Implementation(
            ResourcePrefixDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.RESOURCE_FOLDER_SCOPE));

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end up " +
            "in the same shared app namespace.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    @Nullable
    private String mPrefix;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = mPrefix;
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return;
        }

        // Names with a colon are framework references, not project resource names.
        if (name.indexOf(':') != -1) {
            return;
        }

        if (!name.startsWith(prefix)) {
            Attr attr = element.getAttributeNode("name");
            Location location = attr != null ? context.getLocation(attr)
                    : context.getLocation(element);
            context.report(ISSUE, attr, location,
                    String.format(
                            "Resource named `%1$s` does not start with the project's `%2$s` resource prefix `%3$s`",
                            name, RESOURCE_PREFIX, prefix));
        }
    }

    @Override
    public void checkResourceFolder(@NonNull Context context, @NonNull File folder) {
        String prefix = mPrefix;
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }

        for (File file : children) {
            if (file.isDirectory()) {
                continue;
            }

            String resourceName = LintUtils.getBaseName(file.getName());
            if (!resourceName.startsWith(prefix)) {
                context.report(ISSUE, context.getLocation(file),
                        String.format(
                                "Resource named `%1$s` does not start with the project's `%2$s` resource prefix `%3$s`",
                                resourceName, RESOURCE_PREFIX, prefix));
            }
        }
    }
}